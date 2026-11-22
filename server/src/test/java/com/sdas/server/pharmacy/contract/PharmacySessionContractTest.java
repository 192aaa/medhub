package com.sdas.server.pharmacy.contract;

import static com.sdas.server.pharmacy.contract.PharmacyContractFixtures.seedPharmacy;
import static com.sdas.server.pharmacy.contract.PharmacyContractFixtures.withCsrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.pharmacy.support.PharmacyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 端点 1 药店登录契约测试（T028）：正向字段、统一模糊失败、禁用账号、缺 CSRF 拒绝。
 */
class PharmacySessionContractTest extends PharmacyTestSupport {

    @BeforeEach
    void setUp() {
        seedPharmacy(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        fixtures().cleanUp();
    }

    @Test
    void loginReturnsFullFields() throws Exception {
        mockMvc.perform(login("pharmacy_hp", "password", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.pharmacyId").value(8101))
                .andExpect(jsonPath("$.data.nameMask").value("王*"))
                .andExpect(jsonPath("$.data.pharmacyName").value("示例大药房（总部店）"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200));
    }

    @Test
    void unknownAccountUsesUnifiedMessage() throws Exception {
        mockMvc.perform(login("nobody", "password", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
    }

    @Test
    void disabledAccountIsIndistinguishableFromWrongPassword() throws Exception {
        fixtures().seedDisabledPharmacy();

        mockMvc.perform(login("pharmacy_fixture_disabled", "password", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
        mockMvc.perform(login("pharmacy_hp", "wrong-password", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        mockMvc.perform(login("pharmacy_hp", "password", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private MockHttpServletRequestBuilder login(String username, String password, boolean csrf) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/pharmacy/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (csrf) {
            withCsrf(builder);
        }
        return builder;
    }
}
