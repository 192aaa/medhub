package com.sdas.server.doctor.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 医生接诊契约测试共享夹具：CSRF、医生数据预置、登录 helper。
 */
public final class DoctorContractFixtures {

    /** 测试用 CSRF 值（Cookie 与请求头一致）。 */
    public static final String CSRF = "contract-csrf-token";

    /** 演示医生明文密码（仅测试）。 */
    public static final String PASSWORD = "password";

    /** 明文 "password" 的 BCrypt 哈希（与 V6 种子一致）。 */
    public static final String PASSWORD_HASH =
            "$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DoctorContractFixtures() {
    }

    /** 为修改类请求附加双重 Cookie 校验所需的 Cookie 与头。 */
    public static void withCsrf(MockHttpServletRequestBuilder builder) {
        builder.cookie(new Cookie("sdas_csrf", CSRF)).header("X-CSRF-Token", CSRF);
    }

    /**
     * 预置科室 / 医生 / 医生账号（登录契约前置）。
     *
     * @param jdbcTemplate JdbcTemplate
     */
    public static void seedDoctor(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO department (id, name, enabled) VALUES (1, '内科', 1)");
        jdbcTemplate.update("INSERT IGNORE INTO doctor (id, department_id, name, title, hospital, "
                + "online_status) VALUES (101, 1, '张*', '主任医师', '示例医院', 'ONLINE')");
        jdbcTemplate.update("INSERT IGNORE INTO doctor_account (username, password_hash, doctor_id, "
                        + "name_mask, status) VALUES ('doctor_zhang', ?, 101, '张*', 'ACTIVE')",
                PASSWORD_HASH);
    }

    /** 预置药品目录（开方契约前置）。 */
    public static void seedDrugs(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("INSERT IGNORE INTO drug_catalog (id, name, spec, manufacturer, "
                        + "dosage_form, unit, prescription_only, enabled, source) "
                        + "VALUES (901, '阿莫西林胶囊', '0.25g*20', '示例制药', '胶囊', '盒', 1, 1, 'PRESET')");
    }

    /**
     * 医生登录，返回 access token。
     *
     * @param mockMvc MockMvc
     * @return access token
     * @throws Exception 请求失败
     */
    public static String doctorLogin(MockMvc mockMvc) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/workbench/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"doctor_zhang\",\"password\":\"password\"}");
        withCsrf(builder);
        MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
