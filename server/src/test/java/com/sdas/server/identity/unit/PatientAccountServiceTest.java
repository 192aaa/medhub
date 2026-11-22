package com.sdas.server.identity.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.dto.PatientRegisterRequest;
import com.sdas.server.identity.entity.PatientAccount;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.repository.PatientAgreementConsentRepository;
import com.sdas.server.identity.repository.PatientSessionRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.IdempotencyService;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.MaskService;
import com.sdas.server.identity.service.PatientAccountService;
import com.sdas.server.identity.service.VerificationCodeService;
import com.sdas.server.identity.service.impl.PatientAccountServiceImpl;
import com.sdas.server.identity.support.CookieSupport;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * T041：手机号已注册、协议版本不匹配、密码规则二次校验。
 */
class PatientAccountServiceTest {

    private static final String PHONE = "13800138000";

    private PatientAccountRepository accountRepository;

    private IdempotencyService idempotencyService;

    private PatientAccountService service;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = mock(IdentityProperties.class);
        when(properties.agreement()).thenReturn(new IdentityProperties.Agreement("v1.0"));
        accountRepository = mock(PatientAccountRepository.class);
        idempotencyService = mock(IdempotencyService.class);
        when(idempotencyService.execute(any(), any(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.phoneDigest(anyString())).thenReturn("PHONE_HASH");
        service = new PatientAccountServiceImpl(accountRepository,
                mock(PatientAgreementConsentRepository.class),
                mock(PatientSessionRepository.class),
                mock(VerificationCodeService.class), cryptoService,
                mock(MaskService.class), mock(JwtTokenService.class),
                mock(CookieSupport.class), idempotencyService,
                mock(AuditEventService.class), properties);
    }

    @Test
    void agreementVersionMismatchIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> register(request("abc12345", true, "v0.9")));
        assertEquals(ErrorCode.AGREEMENT_NOT_ACCEPTED, exception.getErrorCode());
    }

    @Test
    void agreementNotAcceptedIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> register(request("abc12345", false, "v1.0")));
        assertEquals(ErrorCode.AGREEMENT_NOT_ACCEPTED, exception.getErrorCode());
    }

    @Test
    void passwordWithoutDigitIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> register(request("abcdefgh", true, "v1.0")));
        assertEquals(ErrorCode.INVALID_PASSWORD_FORMAT, exception.getErrorCode());
    }

    @Test
    void alreadyRegisteredPhoneReturnsUnifiedFailure() {
        when(accountRepository.findByPhoneHash("PHONE_HASH"))
                .thenReturn(Optional.of(mock(PatientAccount.class)));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> register(request("abc12345", true, "v1.0")));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
    }

    private void register(PatientRegisterRequest request) {
        service.register(request, null, new MockHttpServletResponse());
    }

    private PatientRegisterRequest request(String password, boolean accepted, String version) {
        return new PatientRegisterRequest(PHONE, "888888", password, accepted, version);
    }
}
