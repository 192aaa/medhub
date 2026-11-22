package com.sdas.server.identity.contract;

import static com.sdas.server.identity.contract.IdentityContractFixtures.freshPhone;
import static com.sdas.server.identity.contract.IdentityContractFixtures.register;
import static com.sdas.server.identity.contract.IdentityContractFixtures.withCsrf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.identity.support.IdentityTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * T058：端点 3 登录正向字段、双 Cookie、统一失败文案、格式与 CSRF 异常。
 */
class PatientLoginContractTest extends IdentityTestSupport {

    @Test
    void loginReturnsFullFieldsAndBothCookies() throws Exception {
        String phone = freshPhone();
        register(mockMvc, phone);
        var result = mockMvc.perform(login(phone, "abc12345", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.patientId").isNumber())
                .andExpect(jsonPath("$.data.maskedPhone").exists())
                .andExpect(jsonPath("$.data.authStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.consultationAllowed").value(false))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(7200))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();
        assertNotNull(IdentityContractFixtures.refreshCookie(result));
    }

    @Test
    void unknownAccountUsesUnifiedMessage() throws Exception {
        mockMvc.perform(login(freshPhone(), "abc12345", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
    }

    @Test
    void invalidPasswordFormatReturns400() throws Exception {
        mockMvc.perform(login(freshPhone(), "abc", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        mockMvc.perform(login(freshPhone(), "abc12345", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private MockHttpServletRequestBuilder login(String phone, String password, boolean csrf) {
        var builder = MockMvcRequestBuilders.post("/api/v1/patient-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}");
        if (csrf) {
            withCsrf(builder);
        }
        return builder;
    }
}
