package com.sdas.server.identity.integration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.identity.support.IdentityTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * T039：注册主流程、验证码一次性、用途隔离、重复手机号文案、幂等键复用。
 */
class RegisterFlowIT extends IdentityTestSupport {

    private static final String CSRF = "flow-csrf-token";

    @Test
    void registerThenReuseCodeFails() throws Exception {
        String phone = freshPhone();
        sendCode(phone, "REGISTER");
        register(phone, "888888", null).andExpect(status().isOk());
        register(phone, "888888", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
    }

    @Test
    void resetPasswordCodeCannotRegister() throws Exception {
        String phone = freshPhone();
        sendCode(phone, "RESET_PASSWORD");
        register(phone, "888888", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
    }

    @Test
    void alreadyRegisteredPhoneHasSameMessageAsWrongCode() throws Exception {
        String phone = freshPhone();
        sendCode(phone, "REGISTER");
        register(phone, "888888", null).andExpect(status().isOk());
        register(phone, "000000", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
    }

    @Test
    void idempotencyKeyReturnsFirstResult() throws Exception {
        String phone = freshPhone();
        sendCode(phone, "REGISTER");
        var first = register(phone, "888888", "idem-" + phone)
                .andExpect(status().isOk()).andReturn();
        var second = register(phone, "888888", "idem-" + phone)
                .andExpect(status().isOk()).andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(
                first.getResponse().getContentAsString(),
                second.getResponse().getContentAsString());
    }

    private void sendCode(String phone, String purpose) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/verification-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("sdas_csrf", CSRF))
                        .header("X-CSRF-Token", CSRF)
                        .content("{\"phone\":\"" + phone + "\",\"purpose\":\"" + purpose + "\"}"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String phone, String code, String idemKey) throws Exception {
        var builder = MockMvcRequestBuilders.post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie("sdas_csrf", CSRF))
                .header("X-CSRF-Token", CSRF)
                .content("{\"phone\":\"" + phone + "\",\"verificationCode\":\"" + code
                        + "\",\"password\":\"abc12345\",\"agreementAccepted\":true,"
                        + "\"agreementVersion\":\"v1.0\"}");
        if (idemKey != null) {
            builder.header("X-Idempotency-Key", idemKey);
        }
        return mockMvc.perform(builder);
    }

    private String freshPhone() {
        return "13" + String.format("%09d",
                ThreadLocalRandom.current().nextInt(1_000_000_000));
    }
}
