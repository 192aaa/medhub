package com.sdas.server.identity.contract;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.identity.support.IdentityTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * T038：端点 2 契约测试（正向字段完整性、双 Cookie、异常码全覆盖）。
 */
class PatientRegisterContractTest extends IdentityTestSupport {

    private static final String CSRF = "register-csrf-token";

    @Test
    void registerReturnsFullFieldsAndBothCookies() throws Exception {
        String phone = freshPhone();
        sendCode(phone, "REGISTER");
        post(validBody(phone, "888888", "abc12345", true, "v1.0"), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.patientId").isNumber())
                .andExpect(jsonPath("$.data.maskedPhone").exists())
                .andExpect(jsonPath("$.data.authStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.consultationAllowed").value(false))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(7200))
                .andExpect(jsonPath("$.data.registeredAt").isString())
                .andExpect(jsonPath("$.data.agreementVersion").value("v1.0"))
                .andExpect(result -> {
                    var cookies = result.getResponse().getHeaders("Set-Cookie");
                    org.junit.jupiter.api.Assertions.assertTrue(cookies.stream()
                            .anyMatch(value -> value.contains("sdas_refresh=")
                                    && value.contains("HttpOnly")));
                    org.junit.jupiter.api.Assertions.assertTrue(cookies.stream()
                            .anyMatch(value -> value.contains("sdas_csrf=")
                                    && !value.contains("HttpOnly")));
                });
    }

    @Test
    void agreementRejectedReturns400() throws Exception {
        String phone = freshPhone();
        sendCode(phone, "REGISTER");
        post(validBody(phone, "888888", "abc12345", false, "v1.0"), true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void invalidPasswordReturns400() throws Exception {
        String phone = freshPhone();
        sendCode(phone, "REGISTER");
        post(validBody(phone, "888888", "abcdef", true, "v1.0"), true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void wrongCodeReturns400() throws Exception {
        String phone = freshPhone();
        sendCode(phone, "REGISTER");
        post(validBody(phone, "000000", "abc12345", true, "v1.0"), true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        post(validBody(freshPhone(), "888888", "abc12345", true, "v1.0"), false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private void sendCode(String phone, String purpose) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/verification-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("sdas_csrf", CSRF))
                        .header("X-CSRF-Token", CSRF)
                        .content("{\"phone\":\"" + phone + "\",\"purpose\":\"" + purpose + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions post(String json, boolean csrf) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (csrf) {
            builder.cookie(new Cookie("sdas_csrf", CSRF)).header("X-CSRF-Token", CSRF);
        }
        return mockMvc.perform(builder);
    }

    private String validBody(String phone, String code, String password,
                             boolean accepted, String version) {
        return "{\"phone\":\"" + phone + "\",\"verificationCode\":\"" + code
                + "\",\"password\":\"" + password + "\",\"agreementAccepted\":" + accepted
                + ",\"agreementVersion\":\"" + version + "\"}";
    }

    private String freshPhone() {
        return "13" + String.format("%09d",
                ThreadLocalRandom.current().nextInt(1_000_000_000));
    }
}
