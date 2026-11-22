package com.sdas.server.doctor.contract;

import static com.sdas.server.doctor.contract.DoctorContractFixtures.seedDoctor;
import static com.sdas.server.doctor.contract.DoctorContractFixtures.withCsrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.doctor.support.DoctorTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 端点 1 医生登录契约测试（T025）：正向字段、统一模糊失败、缺 CSRF 拒绝。
 */
class WorkbenchSessionsContractTest extends DoctorTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        seedDoctor(jdbcTemplate);
    }

    @Test
    void loginReturnsFullFields() throws Exception {
        mockMvc.perform(login("doctor_zhang", "password", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.doctorId").value(101))
                .andExpect(jsonPath("$.data.nameMask").value("张*"))
                .andExpect(jsonPath("$.data.title").value("主任医师"))
                .andExpect(jsonPath("$.data.departmentId").value(1))
                .andExpect(jsonPath("$.data.expiresIn").value(7200));
    }

    @Test
    void unknownAccountUsesUnifiedMessage() throws Exception {
        mockMvc.perform(login("nobody", "password", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号或密码错误，或验证信息无效"));
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        mockMvc.perform(login("doctor_zhang", "password", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private MockHttpServletRequestBuilder login(String username, String password, boolean csrf) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/workbench/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (csrf) {
            withCsrf(builder);
        }
        return builder;
    }
}
