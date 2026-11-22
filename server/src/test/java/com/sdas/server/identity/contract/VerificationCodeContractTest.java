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
 * T037：端点 1 契约测试（正向 data 不含验证码 + 异常码与 HTTP 状态双一致）。
 */
class VerificationCodeContractTest extends IdentityTestSupport {

    private static final String CSRF = "contract-csrf-token";

    @Test
    void successResponseNeverContainsCode() throws Exception {
        post(body(freshPhone(), "REGISTER"), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.purpose").value("REGISTER"))
                .andExpect(jsonPath("$.data.maskedPhone").exists())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.nextSendAllowedInSeconds").value(60))
                .andExpect(jsonPath("$.data.code").doesNotExist())
                .andExpect(jsonPath("$.data.verificationCode").doesNotExist());
    }

    @Test
    void invalidPhoneReturns400() throws Exception {
        post(body("12345", "REGISTER"), true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void missingPurposeReturnsBadRequest() throws Exception {
        post("{\"phone\":\"13800138000\"}", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        post(body(freshPhone(), "REGISTER"), false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void repeatedSendWithin60SecondsReturns429() throws Exception {
        String phone = freshPhone();
        post(body(phone, "REGISTER"), true).andExpect(status().isOk());
        post(body(phone, "REGISTER"), true)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));
    }

    private ResultActions post(String json, boolean csrf) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/verification-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (csrf) {
            builder.cookie(new Cookie("sdas_csrf", CSRF)).header("X-CSRF-Token", CSRF);
        }
        return mockMvc.perform(builder);
    }

    private String body(String phone, String purpose) {
        return "{\"phone\":\"" + phone + "\",\"purpose\":\"" + purpose + "\"}";
    }

    private String freshPhone() {
        return "13" + String.format("%09d",
                ThreadLocalRandom.current().nextInt(1_000_000_000));
    }
}
