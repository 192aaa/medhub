package com.sdas.server.pharmacy.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.pharmacy.dto.PharmacyLoginRequest;
import com.sdas.server.pharmacy.dto.PharmacyLoginResponse;
import com.sdas.server.pharmacy.entity.PharmacyAccount;
import com.sdas.server.pharmacy.model.PharmacyAccountStatus;
import com.sdas.server.pharmacy.repository.PharmacyAccountRepository;
import com.sdas.server.pharmacy.service.impl.PharmacyAuthServiceImpl;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 药店登录单测（T030）：成功签发与审计、三种失败统一模糊、账号禁用同样失败。 */
class PharmacyAuthServiceTest {

    private static final String PASSWORD_HASH =
            "$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm";

    private PharmacyAccountRepository accountRepository;

    private JwtTokenService jwtTokenService;

    private AuditEventService auditEventService;

    private PharmacyAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(PharmacyAccountRepository.class);
        jwtTokenService = mock(JwtTokenService.class);
        auditEventService = mock(AuditEventService.class);
        service = new PharmacyAuthServiceImpl(accountRepository, jwtTokenService,
                auditEventService);
    }

    @Test
    void loginIssuesTokenAndAuditsSuccess() {
        when(accountRepository.findByUsername("pharmacy_hp"))
                .thenReturn(Optional.of(account(PharmacyAccountStatus.ACTIVE)));
        when(jwtTokenService.issuePharmacy(eq(8101L), anyLong())).thenReturn("jwt-token");
        when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(7200L);

        PharmacyLoginResponse response =
                service.login(new PharmacyLoginRequest("pharmacy_hp", "password"));

        assertEquals("jwt-token", response.accessToken());
        assertEquals(8101L, response.pharmacyId());
        assertEquals("王*", response.nameMask());
        assertEquals("示例大药房（总部店）", response.pharmacyName());
        assertEquals(7200L, response.expiresIn());
        verify(auditEventService).record(eq(AuditEventType.PHARMACY_LOGIN_SUCCESS), eq("SUCCESS"),
                any(), any(), any(), any());
    }

    @Test
    void unknownAccountFailsWithUnifiedCode() {
        when(accountRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new PharmacyLoginRequest("nobody", "password")));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        verify(auditEventService).record(eq(AuditEventType.PHARMACY_LOGIN_FAILED), eq("FAILURE"),
                any(), any(), any(), any());
    }

    @Test
    void disabledAccountFailsWithUnifiedCode() {
        when(accountRepository.findByUsername("pharmacy_hp"))
                .thenReturn(Optional.of(account(PharmacyAccountStatus.DISABLED)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new PharmacyLoginRequest("pharmacy_hp", "password")));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
    }

    @Test
    void wrongPasswordFailsWithUnifiedCode() {
        when(accountRepository.findByUsername("pharmacy_hp"))
                .thenReturn(Optional.of(account(PharmacyAccountStatus.ACTIVE)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new PharmacyLoginRequest("pharmacy_hp", "wrong")));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
    }

    @Test
    void auditSubjectIsUsernameDigestNotPlaintext() {
        when(accountRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service.login(new PharmacyLoginRequest("nobody", "password")));

        verify(auditEventService).record(eq(AuditEventType.PHARMACY_LOGIN_FAILED), eq("FAILURE"),
                eq(null), eq(null), eq(null), eq(expectedDigest("nobody")));
    }

    private static String expectedDigest(String username) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(username.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PharmacyAccount account(PharmacyAccountStatus status) {
        return new PharmacyAccount(8101L, "pharmacy_hp", PASSWORD_HASH, "王*",
                "示例大药房（总部店）", status, LocalDateTime.now(), LocalDateTime.now(), 0);
    }
}
