package com.sdas.server.pharmacy.contract;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** 药店端契约测试共享夹具：CSRF、药店账号预置、登录 helper。 */
public final class PharmacyContractFixtures {

    /** 测试用 CSRF 值（Cookie 与请求头一致）。 */
    public static final String CSRF = "contract-csrf-token";

    /** 演示口令（仅测试）。 */
    public static final String PASSWORD = "password";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PharmacyContractFixtures() {
    }

    /** 为修改类请求附加双重 Cookie 校验所需的 Cookie 与头。 */
    public static void withCsrf(MockHttpServletRequestBuilder builder) {
        builder.cookie(new Cookie("sdas_csrf", CSRF)).header("X-CSRF-Token", CSRF);
    }

    /**
     * 预置启用药店账号（幂等，主键 8101）。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public static void seedPharmacy(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM prescription_dispense_record WHERE pharmacy_id = 8101");
        jdbcTemplate.update("DELETE FROM pharmacy_account WHERE id = 8101");
        jdbcTemplate.update("INSERT INTO pharmacy_account (id, username, password_hash, name_mask, "
                        + "pharmacy_name, status) VALUES (8101, 'pharmacy_hp', ?, '王*', "
                        + "'示例大药房（总部店）', 'ACTIVE')",
                "$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm");
    }

    /**
     * 药店登录，返回 access token。
     *
     * @param mockMvc MockMvc
     * @return access token
     * @throws Exception 请求失败
     */
    public static String pharmacyLogin(MockMvc mockMvc) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/pharmacy/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"pharmacy_hp\",\"password\":\"password\"}");
        withCsrf(builder);
        MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
