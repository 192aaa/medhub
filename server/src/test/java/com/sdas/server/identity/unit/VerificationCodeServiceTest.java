package com.sdas.server.identity.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.dto.VerificationCodeSendRequest;
import com.sdas.server.identity.model.VerificationPurpose;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.MaskService;
import com.sdas.server.identity.service.RateLimitService;
import com.sdas.server.identity.service.VerificationCodeService;
import com.sdas.server.identity.service.impl.VerificationCodeServiceImpl;
import com.sdas.server.identity.support.VerificationCodeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * T040：固定验证码只存摘要、TTL 300 s、60 s 间隔、ACTIVE → USED 原子消费。
 */
class VerificationCodeServiceTest {

    private static final String PHONE = "13800138000";

    private VerificationCodeStore store;

    private RateLimitService rateLimitService;

    private VerificationCodeService service;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = mock(IdentityProperties.class);
        when(properties.verificationCode()).thenReturn(new IdentityProperties.VerificationCode(
                true, "888888", 300L, 60L, 10, 5));
        store = mock(VerificationCodeStore.class);
        rateLimitService = mock(RateLimitService.class);
        CryptoService cryptoService = mock(CryptoService.class);
        when(cryptoService.phoneDigest(PHONE)).thenReturn("PHONE_DIGEST");
        when(cryptoService.codeDigest("888888")).thenReturn("CODE_DIGEST");
        MaskService maskService = mock(MaskService.class);
        when(maskService.maskPhone(PHONE)).thenReturn("138****8000");
        when(rateLimitService.sendIntervalSeconds()).thenReturn(60L);
        service = new VerificationCodeServiceImpl(properties, store, rateLimitService,
                cryptoService, maskService, mock(AuditEventService.class));
    }

    @Test
    void onlyHmacDigestIsStored() {
        service.send(new VerificationCodeSendRequest(PHONE, VerificationPurpose.REGISTER));
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(store).save(eq(VerificationPurpose.REGISTER), eq("PHONE_DIGEST"),
                captor.capture(), anyInt());
        assertEquals("CODE_DIGEST", captor.getValue());
        assertNotEquals("888888", captor.getValue());
    }

    @Test
    void ttlIs300AndIntervalIs60() {
        var response = service.send(
                new VerificationCodeSendRequest(PHONE, VerificationPurpose.REGISTER));
        assertEquals(300L, response.expiresInSeconds());
        assertEquals(60L, response.nextSendAllowedInSeconds());
        assertEquals(VerificationPurpose.REGISTER, response.purpose());
    }

    @Test
    void rateLimitExceptionPropagates() {
        doThrow(new BusinessException(ErrorCode.RATE_LIMITED))
                .when(rateLimitService).checkSend("PHONE_DIGEST");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.send(new VerificationCodeSendRequest(
                        PHONE, VerificationPurpose.REGISTER)));
        assertEquals(ErrorCode.RATE_LIMITED, exception.getErrorCode());
    }

    @Test
    void consumeDelegatesToAtomicStore() {
        service.consume(VerificationPurpose.REGISTER, PHONE, "888888");
        verify(store).verifyAndConsume(VerificationPurpose.REGISTER,
                "PHONE_DIGEST", "CODE_DIGEST");
    }
}
