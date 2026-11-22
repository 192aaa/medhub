package com.sdas.server.identity.integration;

import static com.sdas.server.identity.contract.IdentityContractFixtures.accessToken;
import static com.sdas.server.identity.contract.IdentityContractFixtures.freshPhone;
import static com.sdas.server.identity.contract.IdentityContractFixtures.identityStatus;
import static com.sdas.server.identity.contract.IdentityContractFixtures.patientId;
import static com.sdas.server.identity.contract.IdentityContractFixtures.register;
import static com.sdas.server.identity.contract.IdentityContractFixtures.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.identity.repository.HospitalIdentityWhitelistRepository;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.support.IdentityFixture;
import com.sdas.server.identity.support.IdentityTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T079：实名认证四种比对结果文案一致、幂等、并发绑定唯一、15 分钟限流、503 状态不变。
 */
class IdentityVerificationFlowIT extends IdentityTestSupport {

    private static final String UNIFIED_MESSAGE = "账号或密码错误，或验证信息无效";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CryptoService cryptoService;

    @MockitoSpyBean
    private HospitalIdentityWhitelistRepository whitelistRepository;

    private final List<Long> whitelistIds = new ArrayList<>();

    private IdentityFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new IdentityFixture(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        whitelistIds.forEach(fixture::deleteWhitelist);
        whitelistIds.clear();
    }

    @Test
    void fourMatchOutcomesUseContractCodesAndMessages() throws Exception {
        String hitName = fixture.syntheticName();
        String hitIdCard = fixture.randomIdCardNumber();
        insertWhitelist(hitName, hitIdCard);

        // 1) 白名单命中
        MvcResult hitRegistration = register(mockMvc, freshPhone());
        mockMvc.perform(verify(accessToken(hitRegistration), hitName, hitIdCard))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.authStatus").value("VERIFIED"));

        // 2) 姓名不匹配 3) 证件号未命中 4) 证件号已被他人绑定
        String token = accessToken(register(mockMvc, freshPhone()));
        assertUnifiedFailure(verify(token, fixture.syntheticName(), hitIdCard));
        assertUnifiedFailure(verify(token, fixture.syntheticName(), fixture.randomIdCardNumber()));
        assertUnifiedFailure(verify(token, hitName, hitIdCard));
    }

    @Test
    void repeatedSubmissionAfterVerifiedDoesNotInsertDuplicateIdentity() throws Exception {
        MvcResult registration = register(mockMvc, freshPhone());
        String token = accessToken(registration);
        long accountId = patientId(registration);
        String name = fixture.syntheticName();
        String idCard = fixture.randomIdCardNumber();
        insertWhitelist(name, idCard);

        mockMvc.perform(verify(token, name, idCard)).andExpect(status().isOk());
        mockMvc.perform(verify(token, name, idCard)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authStatus").value("VERIFIED"));

        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_identity WHERE patient_id = ?", Long.class,
                accountId);
        assertEquals(1L, rows);
    }

    @Test
    void concurrentBindingOfSameIdCardOnlyOneSucceeds() throws Exception {
        String tokenA = accessToken(register(mockMvc, freshPhone()));
        String tokenB = accessToken(register(mockMvc, freshPhone()));
        String name = fixture.syntheticName();
        String idCard = fixture.randomIdCardNumber();
        insertWhitelist(name, idCard);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        for (String token : List.of(tokenA, tokenB)) {
            pool.submit(() -> {
                try {
                    start.await();
                    statuses.add(mockMvc.perform(verify(token, name, idCard))
                            .andReturn().getResponse().getStatus());
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
        assertEquals(1, statuses.stream().filter(code -> code == 400).count(),
                "statuses=" + statuses);
    }

    @Test
    void fifteenMinuteWindowRateLimitApplies() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));
        String name = fixture.syntheticName();
        String idCard = fixture.randomIdCardNumber();
        for (int attempt = 0; attempt < 20; attempt++) {
            assertUnifiedFailure(verify(token, name, idCard));
        }
        mockMvc.perform(verify(token, name, idCard))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));
    }

    @Test
    void mysqlFailureReturns503AndLeavesStatusUnverified() throws Exception {
        MvcResult registration = register(mockMvc, freshPhone());
        String token = accessToken(registration);
        long accountId = patientId(registration);
        doThrow(new TransientDataAccessResourceException("mysql down"))
                .when(whitelistRepository).findEnabled(anyString(), anyString());

        mockMvc.perform(verify(token, fixture.syntheticName(), fixture.randomIdCardNumber()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));

        String status = jdbcTemplate.queryForObject(
                "SELECT auth_status FROM patient_account WHERE id = ?", String.class, accountId);
        assertEquals("UNVERIFIED", status);
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_identity WHERE patient_id = ?", Long.class,
                accountId);
        assertEquals(0L, rows);

        // 端点 8 不访问白名单，断言门禁仍关闭
        mockMvc.perform(identityStatus(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consultationAllowed").value(false));
    }

    private void assertUnifiedFailure(MockHttpServletRequestBuilder builder) throws Exception {
        mockMvc.perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(UNIFIED_MESSAGE));
    }

    private void insertWhitelist(String name, String idCard) {
        String digest = cryptoService.idCardDigest(idCard);
        byte[] cipher = cryptoService.encrypt(idCard.getBytes(StandardCharsets.UTF_8));
        whitelistIds.add(fixture.insertWhitelist(name, digest, cipher));
    }
}
