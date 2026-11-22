package com.sdas.server.identity.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 契约测试共享夹具：CSRF 双重 Cookie、注册前置、响应字段提取。
 */
public final class IdentityContractFixtures {

    /** 测试用 CSRF 值（Cookie 与请求头一致） */
    public static final String CSRF = "contract-csrf-token";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IdentityContractFixtures() {
    }

    /** @return 随机 11 位手机号，避免 60 s 发送限流跨用例干扰 */
    public static String freshPhone() {
        return "13" + String.format("%09d",
                ThreadLocalRandom.current().nextInt(1_000_000_000));
    }

    /**
     * 为修改类请求附加双重 Cookie 校验所需的 Cookie 与头。
     *
     * @param builder 请求构造器
     */
    public static void withCsrf(MockHttpServletRequestBuilder builder) {
        builder.cookie(new Cookie("sdas_csrf", CSRF)).header("X-CSRF-Token", CSRF);
    }

    /**
     * 完成一次注册（端点 1 → 端点 2）。
     *
     * @param mockMvc MockMvc
     * @param phone   新手机号
     * @return 注册响应
     * @throws Exception 请求失败
     */
    public static MvcResult register(MockMvc mockMvc, String phone) throws Exception {
        MockHttpServletRequestBuilder send = MockMvcRequestBuilders
                .post("/api/v1/verification-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"purpose\":\"REGISTER\"}");
        withCsrf(send);
        mockMvc.perform(send).andExpect(status().isOk());

        MockHttpServletRequestBuilder create = MockMvcRequestBuilders
                .post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"verificationCode\":\"888888\","
                        + "\"password\":\"abc12345\",\"agreementAccepted\":true,"
                        + "\"agreementVersion\":\"v1.0\"}");
        withCsrf(create);
        return mockMvc.perform(create).andExpect(status().isOk()).andReturn();
    }

    /**
     * 构造端点 2 请求（注册，双重 Cookie CSRF），不自动执行，供负向断言复用。
     *
     * @param phone 手机号
     * @return 请求构造器
     */
    public static MockHttpServletRequestBuilder registerRequest(String phone) {
        MockHttpServletRequestBuilder create = MockMvcRequestBuilders
                .post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"verificationCode\":\"888888\","
                        + "\"password\":\"abc12345\",\"agreementAccepted\":true,"
                        + "\"agreementVersion\":\"v1.0\"}");
        withCsrf(create);
        return create;
    }

    /**
     * 提取注册 / 登录响应中的 access token。
     *
     * @param result MVC 结果
     * @return access token
     * @throws Exception JSON 解析失败
     */
    public static String accessToken(MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    /**
     * 提取注册响应中的患者主键。
     *
     * @param result MVC 结果
     * @return 患者主键
     * @throws Exception JSON 解析失败
     */
    public static long patientId(MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString())
                .path("data").path("patientId").asLong();
    }

    /**
     * 构造端点 7 请求（Bearer + 双重 Cookie CSRF）。
     *
     * @param token       access token
     * @param realName    姓名
     * @param idCardNumber 证件号
     * @return 请求构造器
     */
    public static MockHttpServletRequestBuilder verify(String token, String realName,
                                                       String idCardNumber) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/identity-verifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"realName\":\"" + realName + "\",\"idCardNumber\":\""
                        + idCardNumber + "\"}");
        withCsrf(builder);
        return builder;
    }

    /**
     * 构造端点 8 请求（Bearer，无 CSRF）。
     *
     * @param token access token
     * @return 请求构造器
     */
    public static MockHttpServletRequestBuilder identityStatus(String token) {
        return MockMvcRequestBuilders.get("/api/v1/patients/current/identity-status")
                .header("Authorization", "Bearer " + token);
    }

    /**
     * 构造端点 1 请求（发送验证码，双重 Cookie CSRF）。
     *
     * @param phone   手机号
     * @param purpose 用途（REGISTER / RESET_PASSWORD）
     * @return 请求构造器
     */
    public static MockHttpServletRequestBuilder sendVerificationCode(String phone, String purpose) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/verification-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"purpose\":\"" + purpose + "\"}");
        withCsrf(builder);
        return builder;
    }

    /**
     * 构造端点 6 请求（重置密码，双重 Cookie CSRF）。
     *
     * @param phone       手机号
     * @param code        验证码
     * @param newPassword 新密码（6-20 位字母数字组合）
     * @return 请求构造器
     */
    public static MockHttpServletRequestBuilder passwordReset(String phone, String code,
                                                             String newPassword) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"verificationCode\":\"" + code
                        + "\",\"newPassword\":\"" + newPassword + "\"}");
        withCsrf(builder);
        return builder;
    }

    /**
     * 构造端点 3 请求（登录，双重 Cookie CSRF）。
     *
     * @param phone    手机号
     * @param password 密码
     * @return 请求构造器
     */
    public static MockHttpServletRequestBuilder login(String phone, String password) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/patient-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}");
        withCsrf(builder);
        return builder;
    }

    /**
     * 提取响应头中的 sdas_refresh Cookie 值。
     *
     * @param result MVC 结果
     * @return refresh token，缺失时返回 null
     */
    public static String refreshCookie(MvcResult result) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith("sdas_refresh=")) {
                String value = header.substring("sdas_refresh=".length());
                int semicolon = value.indexOf(';');
                return semicolon < 0 ? value : value.substring(0, semicolon);
            }
        }
        return null;
    }
}
