package com.sdas.server.identity.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.identity.dto.IdentityStatusResponse;
import com.sdas.server.identity.dto.IdentityVerificationRequest;
import com.sdas.server.identity.dto.IdentityVerificationResponse;
import com.sdas.server.identity.entity.HospitalIdentityWhitelist;
import com.sdas.server.identity.entity.PatientAccount;
import com.sdas.server.identity.entity.PatientIdentity;
import com.sdas.server.identity.model.AccountStatus;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.model.AuthStatus;
import com.sdas.server.identity.repository.HospitalIdentityWhitelistRepository;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.repository.PatientIdentityRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.IdentityVerificationService;
import com.sdas.server.identity.service.MaskService;
import com.sdas.server.identity.service.RateLimitService;
import com.sdas.server.identity.service.TokenClaims;
import com.sdas.server.identity.service.impl.IdentityVerificationServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * T080：GB 11643 校验位、uk_id_card_hash 冲突映射、事务回滚边界、
 * authStatus / consultationAllowed 联动判定。
 */
class IdentityVerificationServiceTest {

    private static final long PATIENT_ID = 42L;

    private static final String NAME = "张三";

    private static final String ID_CARD = "11010519491231002X";

    private static final String MASKED_ID_CARD = "110***********002X";

    private PatientIdExtractor patientIdExtractor;

    private PatientAccountRepository accountRepository;

    private PatientIdentityRepository identityRepository;

    private HospitalIdentityWhitelistRepository whitelistRepository;

    private CryptoService cryptoService;

    private MaskService maskService;

    private RateLimitService rateLimitService;

    private AuditEventService auditEventService;

    private IdentityVerificationService service;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        patientIdExtractor = mock(PatientIdExtractor.class);
        accountRepository = mock(PatientAccountRepository.class);
        identityRepository = mock(PatientIdentityRepository.class);
        whitelistRepository = mock(HospitalIdentityWhitelistRepository.class);
        cryptoService = mock(CryptoService.class);
        maskService = mock(MaskService.class);
        rateLimitService = mock(RateLimitService.class);
        auditEventService = mock(AuditEventService.class);
        service = new IdentityVerificationServiceImpl(patientIdExtractor, accountRepository,
                identityRepository, whitelistRepository, cryptoService, maskService,
                rateLimitService, auditEventService);
        request = new MockHttpServletRequest();

        when(patientIdExtractor.extract(any())).thenReturn(new TokenClaims(PATIENT_ID, 7L));
        when(accountRepository.findById(PATIENT_ID)).thenReturn(Optional.of(unverifiedAccount()));
        when(cryptoService.idCardDigest(anyString())).thenReturn("DIGEST");
        when(cryptoService.encrypt(any(byte[].class))).thenReturn(new byte[]{1, 2});
        when(maskService.maskIdCard(anyString())).thenReturn(MASKED_ID_CARD);
        when(maskService.maskName(anyString())).thenReturn("张*");
        when(maskService.utf8Bytes(anyString())).thenReturn(new byte[]{3});
        when(whitelistRepository.findEnabled(anyString(), anyString()))
                .thenReturn(Optional.of(whitelistHit()));
        when(identityRepository.insert(any())).thenReturn(1L);
        when(accountRepository.updateAuthStatus(anyLong(), anyString(), any(), anyInt()))
                .thenReturn(1);
    }

    @Test
    void validChecksumPersistsIdentityAndAccountInSingleTransaction() throws Exception {
        IdentityVerificationResponse response = service.verify(request,
                new IdentityVerificationRequest(NAME, ID_CARD));

        assertEquals(AuthStatus.VERIFIED, response.authStatus());
        assertTrue(response.consultationAllowed());
        assertNotNull(response.verifiedAt());
        assertEquals("张*", response.maskedName());
        assertEquals(MASKED_ID_CARD, response.maskedIdCard());
        verify(identityRepository).insert(any());
        verify(accountRepository).updateAuthStatus(eq(PATIENT_ID), eq("VERIFIED"), any(), eq(0));
        verify(auditEventService).record(eq(AuditEventType.IDENTITY_VERIFIED), eq("SUCCESS"),
                eq(PATIENT_ID), any(), any(), any());
        assertTrue(IdentityVerificationServiceImpl.class
                .getMethod("verify", HttpServletRequest.class, IdentityVerificationRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void checksumValidationRejectsWrongCheckDigit() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verify(request,
                        new IdentityVerificationRequest(NAME, "110105194912310021")));
        assertEquals(ErrorCode.INVALID_ID_CARD_FORMAT, exception.getErrorCode());
        verifyNoInteractions(accountRepository, whitelistRepository, identityRepository,
                rateLimitService);
    }

    @Test
    void nameValidationRejectsBlankOversizeAndControlCharacter() {
        assertEquals(ErrorCode.INVALID_NAME_FORMAT,
                assertThrows(BusinessException.class, () -> service.verify(request,
                        new IdentityVerificationRequest("", ID_CARD))).getErrorCode());
        assertEquals(ErrorCode.INVALID_NAME_FORMAT,
                assertThrows(BusinessException.class, () -> service.verify(request,
                        new IdentityVerificationRequest("张".repeat(51), ID_CARD))).getErrorCode());
        assertEquals(ErrorCode.INVALID_NAME_FORMAT,
                assertThrows(BusinessException.class, () -> service.verify(request,
                        new IdentityVerificationRequest("张\u0001三", ID_CARD))).getErrorCode());
    }

    @Test
    void whitelistMissReturnsUnifiedFailureAndWritesRejectedAudit() {
        when(whitelistRepository.findEnabled(anyString(), anyString()))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verify(request, new IdentityVerificationRequest(NAME, ID_CARD)));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        verify(identityRepository, never()).insert(any());
        verify(auditEventService).record(eq(AuditEventType.IDENTITY_REJECTED), eq("FAILURE"),
                eq(PATIENT_ID), any(), any(), eq("WHITELIST_MISS"));
    }

    @Test
    void idCardHashConflictMapsToUnifiedFailure() {
        when(identityRepository.insert(any()))
                .thenThrow(new BusinessException(ErrorCode.AUTHENTICATION_FAILED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verify(request, new IdentityVerificationRequest(NAME, ID_CARD)));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        verify(accountRepository, never())
                .updateAuthStatus(anyLong(), anyString(), any(), anyInt());
        verify(auditEventService).record(eq(AuditEventType.IDENTITY_REJECTED), eq("FAILURE"),
                eq(PATIENT_ID), any(), any(), eq("ID_CARD_BOUND"));
    }

    @Test
    void optimisticLockConflictRollsBackWithoutSuccessAudit() {
        when(accountRepository.updateAuthStatus(anyLong(), anyString(), any(), anyInt()))
                .thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verify(request, new IdentityVerificationRequest(NAME, ID_CARD)));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        verify(auditEventService, never()).record(eq(AuditEventType.IDENTITY_VERIFIED),
                any(), any(), any(), any(), any());
    }

    @Test
    void verifiedAccountIsIdempotentWithoutRateLimitOrInsert() {
        when(accountRepository.findById(PATIENT_ID)).thenReturn(Optional.of(verifiedAccount()));
        when(identityRepository.findByPatientId(PATIENT_ID)).thenReturn(Optional.of(identity()));

        IdentityVerificationResponse response = service.verify(request,
                new IdentityVerificationRequest(NAME, ID_CARD));

        assertEquals(AuthStatus.VERIFIED, response.authStatus());
        assertEquals("张*", response.maskedName());
        verify(identityRepository, never()).insert(any());
        verify(rateLimitService, never()).checkIdentityVerification(anyString());
    }

    @Test
    void statusStrictlyLinksConsultationAllowedWithAuthStatus() {
        IdentityStatusResponse unverified = service.status(request);
        assertEquals(AuthStatus.UNVERIFIED, unverified.authStatus());
        assertFalse(unverified.consultationAllowed());
        assertNull(unverified.verifiedAt());
        assertNull(unverified.maskedName());
        assertNull(unverified.maskedIdCard());

        when(accountRepository.findById(PATIENT_ID)).thenReturn(Optional.of(verifiedAccount()));
        when(identityRepository.findByPatientId(PATIENT_ID)).thenReturn(Optional.of(identity()));
        IdentityStatusResponse verified = service.status(request);
        assertEquals(AuthStatus.VERIFIED, verified.authStatus());
        assertTrue(verified.consultationAllowed());
        assertNotNull(verified.verifiedAt());
        assertEquals("张*", verified.maskedName());
        assertEquals(MASKED_ID_CARD, verified.maskedIdCard());
    }

    private static PatientAccount unverifiedAccount() {
        return account(AuthStatus.UNVERIFIED, null);
    }

    private static PatientAccount verifiedAccount() {
        return account(AuthStatus.VERIFIED, LocalDateTime.now());
    }

    private static PatientAccount account(AuthStatus status, LocalDateTime verifiedAt) {
        LocalDateTime now = LocalDateTime.now();
        return new PatientAccount(PATIENT_ID, "PHONE_HASH", new byte[]{1}, "138****0000",
                "password-hash", status, AccountStatus.ACTIVE, now, verifiedAt, now, now, 0);
    }

    private static PatientIdentity identity() {
        LocalDateTime now = LocalDateTime.now();
        return new PatientIdentity(1L, PATIENT_ID, new byte[]{1}, "张*", "DIGEST",
                new byte[]{2}, MASKED_ID_CARD, now, now, now, 0);
    }

    private static HospitalIdentityWhitelist whitelistHit() {
        LocalDateTime now = LocalDateTime.now();
        return new HospitalIdentityWhitelist(1L, NAME, "DIGEST", new byte[]{1}, true,
                "PRESET", now, now, 0);
    }
}
