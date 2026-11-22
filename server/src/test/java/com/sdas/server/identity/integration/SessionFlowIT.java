package com.sdas.server.identity.integration;

import static com.sdas.server.identity.contract.IdentityContractFixtures.accessToken;
import static com.sdas.server.identity.contract.IdentityContractFixtures.freshPhone;
import static com.sdas.server.identity.contract.IdentityContractFixtures.patientId;
import static com.sdas.server.identity.contract.IdentityContractFixtures.refreshCookie;
import static com.sdas.server.identity.contract.IdentityContractFixtures.register;
import static com.sdas.server.identity.contract.IdentityContractFixtures.withCsrf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.identity.support.IdentityTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T061：三种失败形态文案一致、登出后 access token 立即失效、并发轮换仅一个成功、
 * 失败达阈值后不锁号。
 */
class SessionFlowIT extends IdentityTestSupport {

    private static final String UNIFIED_MESSAGE = "账号或密码错误，或验证信息无效";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientIdExtractor patientIdExtractor;

    @Test
    void threeFailureModesShareUnifiedMessage() throws Exception {
        String unknownPhone = freshPhone();
        mockMvc.perform(login(unknownPhone, "abc12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(UNIFIED_MESSAGE));

        String registeredPhone = freshPhone();
        register(mockMvc, registeredPhone);
        mockMvc.perform(login(registeredPhone, "zzz99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(UNIFIED_MESSAGE));

        String disabledPhone = freshPhone();
        long disabledId = patientId(register(mockMvc, disabledPhone));
        jdbcTemplate.update("UPDATE patient_account SET account_status = 'DISABLED' WHERE id = ?",
                disabledId);
        mockMvc.perform(login(disabledPhone, "abc12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(UNIFIED_MESSAGE));
    }

    @Test
    void logoutInvalidatesAccessTokenImmediately() throws Exception {
        String phone = freshPhone();
        String token = accessToken(register(mockMvc, phone));

        var logout = delete("/api/v1/patient-sessions/current")
                .header("Authorization", "Bearer " + token);
        withCsrf(logout);
        mockMvc.perform(logout).andExpect(status().isOk());

        MockHttpServletRequest probe = new MockHttpServletRequest();
        probe.addHeader("Authorization", "Bearer " + token);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> patientIdExtractor.extract(probe));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void concurrentRefreshOnlyOneSucceeds() throws Exception {
        String phone = freshPhone();
        String refresh = refreshCookie(register(mockMvc, phone));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    var builder = put("/api/v1/patient-sessions/current/refresh-token")
                            .cookie(new Cookie("sdas_refresh", refresh));
                    withCsrf(builder);
                    statuses.add(mockMvc.perform(builder).andReturn().getResponse().getStatus());
                } catch (Exception failure) {
                    statuses.add(-1);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(2, statuses.size(), "statuses=" + statuses);
        assertEquals(1, statuses.stream().filter(code -> code == 200).count(),
                "statuses=" + statuses);
        assertTrue(statuses.stream().anyMatch(code -> code == 400), "statuses=" + statuses);
    }

    @Test
    void failureThresholdDoesNotLockAccount() throws Exception {
        String phone = freshPhone();
        register(mockMvc, phone);
        for (int attempt = 0; attempt < 21; attempt++) {
            mockMvc.perform(login(phone, "zzz99999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(UNIFIED_MESSAGE));
        }

        MvcResult result = mockMvc.perform(login(phone, "abc12345"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(200, result.getResponse().getStatus());
    }

    private MockHttpServletRequestBuilder login(String phone, String password) {
        var builder = post("/api/v1/patient-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}");
        withCsrf(builder);
        return builder;
    }
}
