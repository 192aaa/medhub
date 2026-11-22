package com.sdas.server.pharmacy.integration;

import static com.sdas.server.pharmacy.contract.PharmacyContractFixtures.pharmacyLogin;
import static com.sdas.server.pharmacy.contract.PharmacyContractFixtures.seedPharmacy;
import static com.sdas.server.pharmacy.contract.PharmacyContractFixtures.withCsrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.pharmacy.support.PharmacyTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 药店扫码验真全链路集成测试（T031）：真实外键链 + 真实签名 + 真实 {@code v1} 令牌。
 *
 * <p>覆盖：扫码成功、手工输入成功、过期与作废以 200 表达、草稿拒绝、验真码错误、
 * 分支互斥、缺失令牌。</p>
 */
class PrescriptionVerificationIT extends PharmacyTestSupport {

    private static final String ENDPOINT = "/api/v1/pharmacy/prescription-verifications";

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        seedPharmacy(jdbcTemplate);
        accessToken = pharmacyLogin(mockMvc);
    }

    @AfterEach
    void tearDown() {
        fixtures().cleanUp();
        fixtures().cleanUpPrescription();
    }

    @Test
    void qrContentVerificationReturnsFullContractFields() throws Exception {
        fixtures().seedSignedPrescription(PrescriptionStatus.SIGNED,
                LocalDateTime.now().minusMinutes(1).withNano(0), LocalDateTime.now().plusDays(3));
        String qrContent = fixtures().qrCodeOfFixture();

        mockMvc.perform(verify("{\"qrContent\":\"" + qrContent + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.verificationResult").value("VALID"))
                .andExpect(jsonPath("$.data.prescriptionId").value(17001))
                .andExpect(jsonPath("$.data.doctorNameMask").value("张*"))
                .andExpect(jsonPath("$.data.status").value("SIGNED"))
                .andExpect(jsonPath("$.data.dispenseStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.dispensable").value(true))
                .andExpect(jsonPath("$.data.expired").value(false))
                .andExpect(jsonPath("$.data.watermark").value("示例医院 电子处方"))
                .andExpect(jsonPath("$.data.verifyCode").isString())
                .andExpect(jsonPath("$.data.items[0].drugId").value(1901))
                .andExpect(jsonPath("$.data.items[0].drugName").value("阿莫西林胶囊"))
                .andExpect(jsonPath("$.data.items[0].spec").value("0.25g*20"));
    }

    @Test
    void manualVerificationWithCorrectCodeSucceeds() throws Exception {
        fixtures().seedSignedPrescription(PrescriptionStatus.SIGNED,
                LocalDateTime.now().minusMinutes(1).withNano(0), LocalDateTime.now().plusDays(3));
        String verifyCode = fixtures().verifyCodeOfFixture();

        mockMvc.perform(verify("{\"prescriptionId\":17001,\"verifyCode\":\"" + verifyCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(17001))
                .andExpect(jsonPath("$.data.dispenseStatus").value("PENDING"));
    }

    @Test
    void manualVerificationWithWrongCodeIsInvalidProof() throws Exception {
        fixtures().seedSignedPrescription(PrescriptionStatus.SIGNED,
                LocalDateTime.now().minusMinutes(1).withNano(0), LocalDateTime.now().plusDays(3));

        mockMvc.perform(verify("{\"prescriptionId\":17001,\"verifyCode\":\"000000000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("验真凭证无效，请重新扫码"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void expiredPrescriptionReturnsTwoHundredWithExpiredStatusOnManualBranch() throws Exception {
        // 手工输入分支不校验令牌 exp，因此能展示「已过期」而非错误页
        fixtures().seedSignedPrescription(PrescriptionStatus.SIGNED,
                LocalDateTime.now().minusDays(5).withNano(0), LocalDateTime.now().minusDays(1));

        mockMvc.perform(verify("{\"prescriptionId\":17001,\"verifyCode\":\""
                        + fixtures().verifyCodeOfFixture() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expired").value(true))
                .andExpect(jsonPath("$.data.dispenseStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.dispensable").value(false));
    }

    @Test
    void expiredPrescriptionStillScansWithinGracePeriod() throws Exception {
        // 令牌 exp = valid_until + 宽限期，处方刚过期仍可解析并展示「已过期」
        fixtures().seedSignedPrescription(PrescriptionStatus.SIGNED,
                LocalDateTime.now().minusDays(5).withNano(0), LocalDateTime.now().minusDays(1));

        mockMvc.perform(verify("{\"qrContent\":\"" + fixtures().qrCodeOfFixture() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expired").value(true))
                .andExpect(jsonPath("$.data.dispenseStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.dispensable").value(false));
    }

    @Test
    void tokenBeyondGracePeriodIsRejectedAsInvalidProof() throws Exception {
        // 超出宽限期（默认 30 天）后凭证本身失效，避免过期二维码长期可被枚举
        fixtures().seedSignedPrescription(PrescriptionStatus.SIGNED,
                LocalDateTime.now().minusDays(40).withNano(0),
                LocalDateTime.now().minusDays(35));

        mockMvc.perform(verify("{\"qrContent\":\"" + fixtures().qrCodeOfFixture() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验真凭证无效，请重新扫码"));
    }

    @Test
    void revokedPrescriptionReturnsTwoHundredWithRevokedStatus() throws Exception {
        fixtures().seedSignedPrescription(PrescriptionStatus.REVOKED,
                LocalDateTime.now().minusMinutes(1).withNano(0), LocalDateTime.now().plusDays(3));

        mockMvc.perform(verify("{\"qrContent\":\"" + fixtures().qrCodeOfFixture() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dispenseStatus").value("REVOKED"))
                .andExpect(jsonPath("$.data.dispensable").value(false));
    }

    @Test
    void draftPrescriptionIsRejectedWithoutExposingFields() throws Exception {
        // 防御性分支：草稿正常不会有 qr_code，这里构造「草稿但凭证可解析」的唯一可达路径
        fixtures().seedSignedPrescription(PrescriptionStatus.SIGNED,
                LocalDateTime.now().minusMinutes(1).withNano(0), LocalDateTime.now().plusDays(3));
        jdbcTemplate.update("UPDATE prescription SET status = 'DRAFT' WHERE id = 17001");

        mockMvc.perform(verify("{\"qrContent\":\"" + fixtures().qrCodeOfFixture() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("处方未签名，无法取药"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void bothBranchesProvidedIsBadRequest() throws Exception {
        fixtures().seedSignedPrescription(PrescriptionStatus.SIGNED,
                LocalDateTime.now().minusMinutes(1).withNano(0), LocalDateTime.now().plusDays(3));

        mockMvc.perform(verify("{\"qrContent\":\"" + fixtures().qrCodeOfFixture()
                        + "\",\"prescriptionId\":17001,\"verifyCode\":\""
                        + fixtures().verifyCodeOfFixture() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void missingTokenIsRejectedAsPharmacyRoleRequired() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrContent\":\"http://localhost:5183/pharmacy/verify?t=v1.aaaa.bbbb\"}");
        withCsrf(builder);

        mockMvc.perform(builder)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private MockHttpServletRequestBuilder verify(String body) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .content(body);
        withCsrf(builder);
        return builder;
    }
}
