package com.sdas.server.identity.contract;

import static com.sdas.server.identity.contract.IdentityContractFixtures.accessToken;
import static com.sdas.server.identity.contract.IdentityContractFixtures.freshPhone;
import static com.sdas.server.identity.contract.IdentityContractFixtures.register;
import static com.sdas.server.identity.contract.IdentityContractFixtures.withCsrf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.identity.support.IdentityTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * T059：端点 4 幂等登出、清除 Cookie、缺凭证 401、CSRF 403。
 */
class PatientLogoutContractTest extends IdentityTestSupport {

    @Test
    void logoutIsIdempotentAndClearsBothCookies() throws Exception {
        String phone = freshPhone();
        String token = accessToken(register(mockMvc, phone));
        var first = logout(token, true);
        mockMvc.perform(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(result -> {
                    var cookies = result.getResponse().getHeaders("Set-Cookie");
                    assertTrue(cookies.stream().anyMatch(v -> v.contains("sdas_refresh=")
                            && v.contains("Max-Age=0")));
                    assertTrue(cookies.stream().anyMatch(v -> v.contains("sdas_csrf=")
                            && v.contains("Max-Age=0")));
                });
        mockMvc.perform(logout(token, true)).andExpect(status().isOk());
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(logout("", true))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        mockMvc.perform(logout("any-token", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private MockHttpServletRequestBuilder logout(String token, boolean csrf) {
        var builder = MockMvcRequestBuilders.delete("/api/v1/patient-sessions/current");
        if (!token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (csrf) {
            withCsrf(builder);
        }
        return builder;
    }
}
