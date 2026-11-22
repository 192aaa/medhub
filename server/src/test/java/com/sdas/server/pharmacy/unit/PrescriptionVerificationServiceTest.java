package com.sdas.server.pharmacy.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.appointment.repository.DoctorRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.doctor.entity.DrugCatalog;
import com.sdas.server.doctor.entity.Prescription;
import com.sdas.server.doctor.entity.PrescriptionItem;
import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.doctor.repository.DrugCatalogRepository;
import com.sdas.server.doctor.repository.PrescriptionItemRepository;
import com.sdas.server.doctor.repository.PrescriptionRepository;
import com.sdas.server.doctor.support.PrescriptionSigningService;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.pharmacy.dto.VerificationRequest;
import com.sdas.server.pharmacy.dto.VerificationResponse;
import com.sdas.server.pharmacy.entity.DispenseRecord;
import com.sdas.server.pharmacy.entity.PharmacyAccount;
import com.sdas.server.pharmacy.model.PharmacyAccountStatus;
import com.sdas.server.pharmacy.repository.DispenseRecordRepository;
import com.sdas.server.pharmacy.repository.PharmacyAccountRepository;
import com.sdas.server.pharmacy.service.impl.PrescriptionVerificationServiceImpl;
import com.sdas.server.pharmacy.support.PrescriptionQrTokenService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 处方验真单测（T029 矩阵，Service 层）：成功、过期、作废、已取药、草稿、内容签名被篡改、
 * 分支互斥、验真码错误、处方不存在。
 */
class PrescriptionVerificationServiceTest {

    private static final long PID = 7001L;

    private static final String TOKEN = "v1.cipher.signature";

    private static final String VERIFY_CODE = "Ab3dE9fG7hJk";

    private PrescriptionQrTokenService tokenService;

    private PrescriptionRepository prescriptionRepository;

    private PrescriptionItemRepository itemRepository;

    private DrugCatalogRepository drugCatalogRepository;

    private DispenseRecordRepository dispenseRecordRepository;

    private PharmacyAccountRepository pharmacyAccountRepository;

    private PrescriptionSigningService signingService;

    private PrescriptionVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        tokenService = mock(PrescriptionQrTokenService.class);
        prescriptionRepository = mock(PrescriptionRepository.class);
        itemRepository = mock(PrescriptionItemRepository.class);
        drugCatalogRepository = mock(DrugCatalogRepository.class);
        dispenseRecordRepository = mock(DispenseRecordRepository.class);
        pharmacyAccountRepository = mock(PharmacyAccountRepository.class);
        signingService = mock(PrescriptionSigningService.class);
        service = new PrescriptionVerificationServiceImpl(tokenService, prescriptionRepository,
                itemRepository, drugCatalogRepository, dispenseRecordRepository,
                pharmacyAccountRepository, mock(DoctorRepository.class), signingService,
                mock(AuditEventService.class));

        when(tokenService.extractToken(anyString())).thenReturn(TOKEN);
        when(tokenService.verify(TOKEN)).thenReturn(PID);
        when(tokenService.verifyCode(TOKEN)).thenReturn(VERIFY_CODE);
        when(tokenService.matchesVerifyCode(TOKEN, VERIFY_CODE)).thenReturn(true);
        when(tokenService.auditDigest(anyString())).thenReturn("0123456789abcdef");
        when(signingService.verify(anyString(), anyString())).thenReturn(true);
        when(itemRepository.findByPrescriptionId(PID)).thenReturn(List.of(item()));
        when(drugCatalogRepository.findById(901L)).thenReturn(Optional.of(drug()));
    }

    @Test
    void validTokenReturnsPendingAndItemDetails() {
        givenPrescription(PrescriptionStatus.SIGNED, LocalDateTime.now().plusDays(2));
        when(dispenseRecordRepository.findByPrescriptionId(PID)).thenReturn(Optional.empty());

        VerificationResponse response = service.verify(qrRequest());

        assertEquals("VALID", response.verificationResult());
        assertEquals(PID, response.prescriptionId());
        assertEquals("SIGNED", response.status());
        assertEquals("PENDING", response.dispenseStatus());
        assertTrue(response.dispensable());
        assertFalse(response.expired());
        assertEquals(VERIFY_CODE, response.verifyCode());
        assertEquals("阿莫西林胶囊", response.items().get(0).drugName());
        assertNull(response.dispensedAt());
        assertNull(response.dispensedByMask());
        assertNull(response.dispenseRecordId());
    }

    @Test
    void expiredPrescriptionReturnsTwoHundredWithExpiredStatus() {
        givenPrescription(PrescriptionStatus.SIGNED, LocalDateTime.now().minusDays(1));
        when(dispenseRecordRepository.findByPrescriptionId(PID)).thenReturn(Optional.empty());

        VerificationResponse response = service.verify(qrRequest());

        assertEquals("EXPIRED", response.dispenseStatus());
        assertTrue(response.expired());
        assertFalse(response.dispensable());
    }

    @Test
    void revokedPrescriptionReturnsTwoHundredWithRevokedStatus() {
        givenPrescription(PrescriptionStatus.REVOKED, LocalDateTime.now().plusDays(1));
        when(dispenseRecordRepository.findByPrescriptionId(PID)).thenReturn(Optional.empty());

        VerificationResponse response = service.verify(qrRequest());

        assertEquals("REVOKED", response.dispenseStatus());
        assertFalse(response.dispensable());
    }

    @Test
    void dispensedPrescriptionExposesRecordAndMaskedDispenser() {
        givenPrescription(PrescriptionStatus.SIGNED, LocalDateTime.now().plusDays(1));
        when(dispenseRecordRepository.findByPrescriptionId(PID))
                .thenReturn(Optional.of(record()));
        when(pharmacyAccountRepository.findById(8101L)).thenReturn(Optional.of(pharmacy()));

        VerificationResponse response = service.verify(qrRequest());

        assertEquals("DISPENSED", response.dispenseStatus());
        assertFalse(response.dispensable());
        assertEquals(5001L, response.dispenseRecordId());
        assertEquals("王*", response.dispensedByMask());
        assertEquals(LocalDateTime.of(2026, 11, 8, 12, 0, 0), response.dispensedAt());
    }

    @Test
    void draftPrescriptionIsRejectedAsNotSigned() {
        givenPrescription(PrescriptionStatus.DRAFT, LocalDateTime.now().plusDays(1));

        assertCode(ErrorCode.PRESCRIPTION_NOT_SIGNED, () -> service.verify(qrRequest()));
    }

    @Test
    void tamperedContentSignatureIsRejected() {
        givenPrescription(PrescriptionStatus.SIGNED, LocalDateTime.now().plusDays(1));
        when(signingService.verify(anyString(), anyString())).thenReturn(false);

        assertCode(ErrorCode.INVALID_VERIFICATION_PROOF, () -> service.verify(qrRequest()));
    }

    @Test
    void bothBranchesOrNeitherAreBadRequest() {
        assertCode(ErrorCode.BAD_REQUEST,
                () -> service.verify(new VerificationRequest(null, null, null)));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.verify(
                new VerificationRequest("http://localhost:5183/pharmacy/verify?t=v1.a.b", PID,
                        VERIFY_CODE)));
    }

    @Test
    void manualBranchRequiresVerifyCode() {
        assertCode(ErrorCode.BAD_REQUEST,
                () -> service.verify(new VerificationRequest(null, PID, null)));
    }

    @Test
    void manualBranchWithWrongCodeIsInvalidProof() {
        givenPrescription(PrescriptionStatus.SIGNED, LocalDateTime.now().plusDays(1));
        when(tokenService.matchesVerifyCode(TOKEN, "999999999999")).thenReturn(false);

        assertCode(ErrorCode.INVALID_VERIFICATION_PROOF,
                () -> service.verify(new VerificationRequest(null, PID, "999999999999")));
    }

    @Test
    void manualBranchWithCorrectCodeSucceeds() {
        givenPrescription(PrescriptionStatus.SIGNED, LocalDateTime.now().plusDays(1));
        when(dispenseRecordRepository.findByPrescriptionId(PID)).thenReturn(Optional.empty());

        VerificationResponse response = service.verify(new VerificationRequest(null, PID, VERIFY_CODE));

        assertEquals(PID, response.prescriptionId());
        assertEquals("PENDING", response.dispenseStatus());
    }

    @Test
    void missingPrescriptionIsNotFound() {
        when(prescriptionRepository.findById(PID)).thenReturn(Optional.empty());

        assertCode(ErrorCode.PRESCRIPTION_NOT_FOUND, () -> service.verify(qrRequest()));
    }

    private void givenPrescription(PrescriptionStatus status, LocalDateTime validUntil) {
        when(prescriptionRepository.findById(PID)).thenReturn(Optional.of(prescription(status,
                validUntil)));
    }

    private void assertCode(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        BusinessException exception = assertThrows(BusinessException.class, action);
        assertEquals(expected, exception.getErrorCode());
    }

    private VerificationRequest qrRequest() {
        return new VerificationRequest("http://localhost:5183/pharmacy/verify?t=" + TOKEN, null, null);
    }

    private Prescription prescription(PrescriptionStatus status, LocalDateTime validUntil) {
        LocalDateTime signedAt = LocalDateTime.of(2026, 11, 8, 10, 12, 0);
        return new Prescription(PID, 701L, 101L, 301L, status, "content-signature", TOKEN,
                "示例医院 电子处方", signedAt, validUntil, signedAt, null, 0);
    }

    private PrescriptionItem item() {
        return new PrescriptionItem(1L, PID, 901L, "0.25g*20", "口服，一次 2 粒，一日 3 次",
                "青霉素过敏者禁用", null);
    }

    private DrugCatalog drug() {
        return new DrugCatalog(901L, "阿莫西林胶囊", "0.25g*20", "示例制药", "胶囊", "盒", true,
                true, "PRESET", null, null, 0);
    }

    private DispenseRecord record() {
        return new DispenseRecord(5001L, PID, 8101L, LocalDateTime.of(2026, 11, 8, 12, 0, 0),
                "v1", "digest", LocalDateTime.of(2026, 11, 8, 12, 0, 0));
    }

    private PharmacyAccount pharmacy() {
        return new PharmacyAccount(8101L, "pharmacy_hp", "hash", "王*", "示例大药房",
                PharmacyAccountStatus.ACTIVE, null, null, 0);
    }
}
