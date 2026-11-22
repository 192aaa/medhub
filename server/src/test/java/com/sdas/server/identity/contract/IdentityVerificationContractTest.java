package com.sdas.server.identity.contract;

import static com.sdas.server.identity.contract.IdentityContractFixtures.accessToken;
import static com.sdas.server.identity.contract.IdentityContractFixtures.freshPhone;
import static com.sdas.server.identity.contract.IdentityContractFixtures.identityStatus;
import static com.sdas.server.identity.contract.IdentityContractFixtures.register;
import static com.sdas.server.identity.contract.IdentityContractFixtures.verify;
import static com.sdas.server.identity.contract.IdentityContractFixtures.withCsrf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.identity.repository.HospitalIdentityWhitelistRepository;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.support.IdentityFixture;
import com.sdas.server.identity.support.IdentityTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * T077：端点 7 正向字段、格式/鉴权/CSRF/限流/依赖异常，且不返回白名单命中信息。
 */
class IdentityVerificationContractTest extends IdentityTestSupport {

    private static final String UNIFIED_MESSAGE = "账号或密码错误，或验证信息无效";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CryptoService cryptoService;

    @MockitoSpyBean
    private HospitalIdentityWhitelistRepository whitelistRepository;

    private final List<Long> whitelistIds = new ArrayList<>();

    private IdentityFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new IdentityFixture(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        whitelistIds.forEach(fixture::deleteWhitelist);
        whitelistIds.clear();
    }

    @Test
    void verificationReturnsVerifiedFieldsWithoutWhitelistHints() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));
        String name = fixture.syntheticName();
        String idCard = fixture.randomIdCardNumber();
        insertWhitelist(name, idCard);

        MvcResult result = mockMvc.perform(verify(token, name, idCard))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.authStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.consultationAllowed").value(true))
                .andExpect(jsonPath("$.data.verifiedAt").isString())
                .andExpect(jsonPath("$.data.maskedName").value(name.substring(0, 1) + "*"))
                .andExpect(jsonPath("$.data.maskedIdCard").value(
                        idCard.substring(0, 3) + "***********" + idCard.substring(14)))
                .andExpect(jsonPath("$.data.identityVerificationResult").doesNotExist())
                .andExpect(jsonPath("$.data.whitelistHit").doesNotExist())
                .andExpect(jsonPath("$.data.idCardHash").doesNotExist())
                .andExpect(jsonPath("$.data.idCardCipher").doesNotExist())
                .andExpect(jsonPath("$.data.realNameCipher").doesNotExist())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("IDENTITY_NOT_VERIFIED"), body);
        assertFalse(body.contains("IDENTITY_REJECTED"), body);
        assertFalse(body.contains("WHITELIST_MISS"), body);
    }

    @Test
    void invalidNameAndIdCardFormatsReturnDedicatedCodes() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));
        String idCard = fixture.randomIdCardNumber();

        mockMvc.perform(verify(token, "", idCard))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("姓名长度须为 1-50 个字符"));
        mockMvc.perform(verify(token, "张".repeat(51), idCard))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("姓名长度须为 1-50 个字符"));
        mockMvc.perform(verify(token, "张三", "110105194912310021"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("身份证号须为 18 位且校验位有效"));
    }

    @Test
    void whitelistMissUsesUnifiedMessage() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));
        mockMvc.perform(verify(token, fixture.syntheticName(), fixture.randomIdCardNumber()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(UNIFIED_MESSAGE));
    }

    @Test
    void missingBearerReturns401() throws Exception {
        var builder = MockMvcRequestBuilders.post("/api/v1/identity-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"realName\":\"张三\",\"idCardNumber\":\"11010519491231002X\"}");
        withCsrf(builder);
        mockMvc.perform(builder)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/identity-verifications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"张三\",\"idCardNumber\":"
                                + "\"11010519491231002X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void rateLimitWindowReturns429() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));
        String name = fixture.syntheticName();
        String idCard = fixture.randomIdCardNumber();
        for (int attempt = 0; attempt < 20; attempt++) {
            mockMvc.perform(verify(token, name, idCard))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(verify(token, name, idCard))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("操作过于频繁，请稍后重试"));
    }

    @Test
    void dependencyFailureReturns503AndKeepsUnverified() throws Exception {
        String token = accessToken(register(mockMvc, freshPhone()));
        doThrow(new TransientDataAccessResourceException("mysql down"))
                .when(whitelistRepository).findEnabled(anyString(), anyString());

        mockMvc.perform(verify(token, fixture.syntheticName(), fixture.randomIdCardNumber()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));

        // 端点 8 不访问白名单，可安全查询：认证状态保持 UNVERIFIED
        mockMvc.perform(identityStatus(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.consultationAllowed").value(false));
    }

    private void insertWhitelist(String name, String idCard) {
        String digest = cryptoService.idCardDigest(idCard);
        byte[] cipher = cryptoService.encrypt(idCard.getBytes(StandardCharsets.UTF_8));
        whitelistIds.add(fixture.insertWhitelist(name, digest, cipher));
    }
}
