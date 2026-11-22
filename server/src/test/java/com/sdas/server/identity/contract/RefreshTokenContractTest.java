package com.sdas.server.identity.contract;

import static com.sdas.server.identity.contract.IdentityContractFixtures.CSRF;
import static com.sdas.server.identity.contract.IdentityContractFixtures.freshPhone;
import static com.sdas.server.identity.contract.IdentityContractFixtures.refreshCookie;
import static com.sdas.server.identity.contract.IdentityContractFixtures.register;
import static com.sdas.server.identity.contract.IdentityContractFixtures.withCsrf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.identity.support.IdentityTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * T060：端点 5 轮换成功、旧凭证复用失败、缺 Cookie 401、CSRF 403。
 */
class RefreshTokenContractTest extends IdentityTestSupport {

    @Test
    void rotateOnceAndRejectOldToken() throws Exception {
        String phone = freshPhone();
        String oldRefresh = refreshCookie(register(mockMvc, phone));
        assertNotNull(oldRefresh);

        MvcResult rotated = mockMvc.perform(refresh(oldRefresh, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(7200))
                .andReturn();
        assertNotNull(refreshCookie(rotated));

        mockMvc.perform(refresh(oldRefresh, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
    }

    @Test
    void missingRefreshCookieReturns401() throws Exception {
        mockMvc.perform(refresh(null, true))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        mockMvc.perform(refresh("any-token", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private MockHttpServletRequestBuilder refresh(String token, boolean csrf) {
        var builder = MockMvcRequestBuilders.put(
                "/api/v1/patient-sessions/current/refresh-token");
        if (token != null) {
            builder.cookie(new Cookie("sdas_refresh", token));
        }
        if (csrf) {
            withCsrf(builder);
            builder.header("X-CSRF-Token", CSRF);
        }
        return builder;
    }
}
