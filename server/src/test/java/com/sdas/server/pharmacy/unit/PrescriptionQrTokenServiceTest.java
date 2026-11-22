package com.sdas.server.pharmacy.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.config.PharmacyProperties;
import com.sdas.server.doctor.support.PrescriptionSigningService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.impl.CryptoServiceImpl;
import com.sdas.server.pharmacy.support.PrescriptionQrTokenService;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/** 二维码令牌单测（T021）：往返、篡改、过期、结构、版本、验真码与长度分层。 */
class PrescriptionQrTokenServiceTest {

    private static final String AES_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final long PID = 123456L;

    /** 与 sdas.pharmacy.qr.token-grace-days 默认值一致。 */
    private static final int GRACE_DAYS = 30;

    @TempDir
    private Path keystoreDirectory;

    @Test
    void issueThenVerifyRoundTrip() {
        PrescriptionQrTokenService service = service();
        String token = service.issue(PID, LocalDateTime.now().plusDays(3));

        assertTrue(token.startsWith("v1."), token);
        assertEquals(PID, service.verify(token));
        assertTrue(token.length() <= 700);
        assertTrue(service.linkOf(token).length() <= 1024);
    }

    @Test
    void tamperedCipherFailsVerification() {
        PrescriptionQrTokenService service = service();
        String token = service.issue(PID, LocalDateTime.now().plusDays(3));
        assertInvalidProof(() -> service.verify(flip(token, 1)));
    }

    @Test
    void tamperedSignatureFailsVerification() {
        PrescriptionQrTokenService service = service();
        String token = service.issue(PID, LocalDateTime.now().plusDays(3));
        assertInvalidProof(() -> service.verify(flip(token, 2)));
    }

    @Test
    void tokenOutlivesPrescriptionByGracePeriod() {
        PrescriptionQrTokenService service = service();
        // 处方已过期 1 天，但令牌仍在宽限期内：可解析，以便药店看到「已过期」状态
        String token = service.issue(PID, LocalDateTime.now().minusDays(1));
        assertEquals(PID, service.verify(token));
    }

    @Test
    void tokenBeyondGracePeriodFailsVerification() {
        PrescriptionQrTokenService service = service();
        String token = service.issue(PID, LocalDateTime.now().minusDays(GRACE_DAYS + 1L));
        assertInvalidProof(() -> service.verify(token));
    }

    @Test
    void malformedOrForeignVersionIsRejected() {
        PrescriptionQrTokenService service = service();
        assertInvalidProof(() -> service.verify("abc"));
        assertInvalidProof(() -> service.verify("v1.!!!.???"));
        String token = service.issue(PID, LocalDateTime.now().plusDays(1));
        assertInvalidProof(() -> service.verify("v2" + token.substring(2)));
        assertInvalidProof(() -> service.verify(null));
    }

    @Test
    void verifyCodeIsSignaturePrefixAndConstantTimeCompared() {
        PrescriptionQrTokenService service = service();
        String token = service.issue(PID, LocalDateTime.now().plusDays(1));
        String code = service.verifyCode(token);

        assertEquals(12, code.length());
        assertTrue(service.matchesVerifyCode(token, code));
        assertFalse(service.matchesVerifyCode(token, "000000000000"));
        assertFalse(service.matchesVerifyCode(token, null));
    }

    @Test
    void linkAndTokenCanBeExtractedFromQrContent() {
        PrescriptionQrTokenService service = service();
        String token = service.issue(PID, LocalDateTime.now().plusDays(1));
        String link = service.linkOf(token);

        assertTrue(link.startsWith("http://localhost:5183/pharmacy/verify?t=v1."));
        assertEquals(token, service.extractToken(link));
        assertEquals(token, service.extractToken(token));
        assertEquals(PID, service.verify(link));
    }

    @Test
    void digestsHaveContractLengths() {
        PrescriptionQrTokenService service = service();
        String token = service.issue(PID, LocalDateTime.now().plusDays(1));

        assertEquals(64, service.proofDigest(token).length());
        assertEquals(16, service.auditDigest(token).length());
        assertTrue(service.proofDigest(token).startsWith(service.auditDigest(token)));
    }

    private String flip(String token, int segment) {
        String[] parts = token.split("\\.");
        char[] chars = parts[segment].toCharArray();
        chars[0] = chars[0] == 'A' ? 'B' : 'A';
        parts[segment] = new String(chars);
        return String.join(".", parts);
    }

    private void assertInvalidProof(org.junit.jupiter.api.function.Executable executable) {
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertEquals(ErrorCode.INVALID_VERIFICATION_PROOF, exception.getErrorCode());
    }

    private PrescriptionQrTokenService service() {
        DoctorProperties doctorProperties = new DoctorProperties(24, 1000, 3,
                new DoctorProperties.Signature(
                        keystoreDirectory.resolve("doctor-signing.jks").toString(),
                        "doctor-signing", "changeit", "SHA256withRSA"),
                new DoctorProperties.Prescription(
                        "http://localhost/verify?prescriptionId=%s&sig=%s", "示例医院"));
        PrescriptionSigningService signingService =
                new PrescriptionSigningService(doctorProperties, new MockEnvironment());
        IdentityProperties identityProperties = new IdentityProperties(null, null,
                new IdentityProperties.Jwt(7200, 604800, "test-only-jwt-secret-0000000000000000"),
                new IdentityProperties.Crypto("idc-secret", "phone-secret", AES_KEY), null);
        CryptoService cryptoService = new CryptoServiceImpl(identityProperties);
        PharmacyProperties pharmacyProperties = new PharmacyProperties(
                new PharmacyProperties.Qr("http://localhost:5183/pharmacy/verify?t=%s", "v1",
                        12, 480, "M", GRACE_DAYS));
        return new PrescriptionQrTokenService(cryptoService, signingService, pharmacyProperties,
                new ObjectMapper());
    }
}
