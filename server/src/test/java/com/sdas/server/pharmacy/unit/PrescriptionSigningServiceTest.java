package com.sdas.server.pharmacy.unit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.doctor.support.PrescriptionSigningService;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/**
 * 处方签名单测（T017）：签名可验真、篡改验真失败、同一 keystore 跨实例仍可验签、prod 缺库启动失败。
 */
class PrescriptionSigningServiceTest {

    @TempDir
    private Path keystoreDirectory;

    @Test
    void signThenVerifyReturnsTrue() {
        PrescriptionSigningService service = service();
        String signature = service.sign("1|101|701|901:0.25g*20:每日三次:饭后");
        assertNotNull(signature);
        assertTrue(service.verify("1|101|701|901:0.25g*20:每日三次:饭后", signature));
    }

    @Test
    void verifyTamperedContentReturnsFalse() {
        PrescriptionSigningService service = service();
        String signature = service.sign("original-content");
        assertFalse(service.verify("tampered-content", signature));
    }

    @Test
    void verifyRejectsMalformedBase64() {
        assertFalse(service().verify("content", "not-base64!!"));
    }

    @Test
    void signatureStaysVerifiableAfterRestart() {
        PrescriptionSigningService before = service();
        byte[] signature = before.signRaw("restart-payload");

        PrescriptionSigningService after = service();
        assertTrue(after.verifyRaw("restart-payload", signature));
        assertTrue(after.verify("restart-payload", Base64.getEncoder().encodeToString(signature)));
        assertArrayEquals(before.publicKey().getEncoded(), after.publicKey().getEncoded());
    }

    @Test
    void prodProfileWithoutKeystoreFailsAtStartup() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        PrescriptionSigningService service =
                new PrescriptionSigningService(properties(), prod);

        BusinessException exception =
                assertThrows(BusinessException.class, service::afterPropertiesSet);
        assertEquals(ErrorCode.PRESCRIPTION_SIGN_FAILED, exception.getErrorCode());
    }

    private PrescriptionSigningService service() {
        return new PrescriptionSigningService(properties(), new MockEnvironment());
    }

    private DoctorProperties properties() {
        return new DoctorProperties(24, 1000, 3,
                new DoctorProperties.Signature(
                        keystoreDirectory.resolve("doctor-signing.jks").toString(),
                        "doctor-signing", "changeit", "SHA256withRSA"),
                new DoctorProperties.Prescription(
                        "http://localhost/verify?prescriptionId=%s&sig=%s", "示例医院"));
    }
}
