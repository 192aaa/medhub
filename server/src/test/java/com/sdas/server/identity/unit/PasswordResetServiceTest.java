package com.sdas.server.identity.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.dto.PasswordResetRequest;
import com.sdas.server.identity.entity.PatientAccount;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.model.VerificationPurpose;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.repository.PatientSessionRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.PasswordResetService;
import com.sdas.server.identity.service.VerificationCodeService;
import com.sdas.server.identity.service.impl.PasswordResetServiceImpl;
import com.sdas.server.identity.support.RedisKeys;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * T095：重置密码服务单测（事务边界、统一失败、乐观锁、会话撤销标记有效期）。
 */
class PasswordResetServiceTest {

    private PatientAccountRepository accountRepository;

    private PatientSessionRepository sessionRepository;

    private VerificationCodeService verificationCodeService;

    private CryptoService cryptoService;

    private AuditEventService auditEventService;

    private StringRedisTemplate redisTemplate;

    private JwtTokenService jwtTokenService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(PatientAccountRepository.class);
        sessionRepository = mock(PatientSessionRepository.class);
        verificationCodeService = mock(VerificationCodeService.class);
        cryptoService = mock(CryptoService.class);
        auditEventService = mock(AuditEventService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        jwtTokenService = mock(JwtTokenService.class);
        when(redisTemplate.opsForValue()).thenReturn(mock());
        when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(7200L);
        service = new PasswordResetServiceImpl(accountRepository, sessionRepository,
                verificationCodeService, cryptoService, auditEventService, redisTemplate,
                jwtTokenService);

        when(cryptoService.phoneDigest(anyString())).thenReturn("PHONE_HASH");
        PatientAccount account = new PatientAccount(1L, "PHONE_HASH", new byte[]{1}, "138****0000",
                "old-hash", com.sdas.server.identity.model.AuthStatus.UNVERIFIED,
                com.sdas.server.identity.model.AccountStatus.ACTIVE, LocalDateTime.now(), null,
                LocalDateTime.now(), LocalDateTime.now(), 0);
        when(accountRepository.findByPhoneHash("PHONE_HASH")).thenReturn(Optional.of(account));
        when(accountRepository.updatePasswordHash(anyLong(), anyString(), anyInt())).thenReturn(1);
        when(sessionRepository.findActiveSessionIds(anyLong())).thenReturn(List.of(1L));
        when(sessionRepository.revokeAllByPatientId(anyLong())).thenReturn(1);
    }

    @Test
    void successConsumesCodeUpdatesPasswordRevokesSessionsAndAudits() throws Exception {
        service.reset(new PasswordResetRequest("13800138000", "888888", "xyz98765"));

        verify(verificationCodeService).consume(eq(VerificationPurpose.RESET_PASSWORD),
                eq("13800138000"), eq("888888"));
        verify(accountRepository).updatePasswordHash(eq(1L), anyString(), eq(0));
        verify(sessionRepository).revokeAllByPatientId(1L);
        verify(verificationCodeService).clear(eq(VerificationPurpose.RESET_PASSWORD),
                eq("13800138000"));
        verify(auditEventService).record(eq(AuditEventType.PASSWORD_RESET), eq("SUCCESS"),
                eq(1L), any(), any(), any());
        assertTrue(PasswordResetServiceImpl.class
                .getMethod("reset", PasswordResetRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void revokedMarkerUsesAccessTokenTtl() {
        when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(3600L);

        service.reset(new PasswordResetRequest("13800138000", "888888", "xyz98765"));

        verify(redisTemplate.opsForValue()).set(eq(RedisKeys.sessionRevoked(1L)), eq("1"),
                eq(Duration.ofSeconds(3600L)));
    }

    @Test
    void unregisteredPhoneUsesUnifiedFailure() {
        when(accountRepository.findByPhoneHash("PHONE_HASH")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reset(new PasswordResetRequest("13800138000", "888888", "xyz98765")));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        verify(verificationCodeService, never()).consume(any(), anyString(), anyString());
        verify(accountRepository, never()).updatePasswordHash(anyLong(), anyString(), anyInt());
    }

    @Test
    void wrongCodePropagatesUnifiedFailure() {
        doThrow(new BusinessException(ErrorCode.AUTHENTICATION_FAILED))
                .when(verificationCodeService).consume(any(), anyString(), anyString());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reset(new PasswordResetRequest("13800138000", "000000", "xyz98765")));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        verify(accountRepository, never()).updatePasswordHash(anyLong(), anyString(), anyInt());
        verify(sessionRepository, never()).revokeAllByPatientId(anyLong());
    }

    @Test
    void optimisticLockConflictRollsBackWithoutAudit() {
        when(accountRepository.updatePasswordHash(anyLong(), anyString(), anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reset(new PasswordResetRequest("13800138000", "888888", "xyz98765")));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        verify(sessionRepository, never()).revokeAllByPatientId(anyLong());
        verify(auditEventService, never()).record(eq(AuditEventType.PASSWORD_RESET),
                any(), any(), any(), any(), any());
    }

    @Test
    void weakPasswordRejectedBeforeCodeConsumption() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reset(new PasswordResetRequest("13800138000", "888888", "abcdef")));

        assertEquals(ErrorCode.INVALID_PASSWORD_FORMAT, exception.getErrorCode());
        verifyNoInteractions(verificationCodeService);
        verifyNoInteractions(accountRepository);
    }
}
