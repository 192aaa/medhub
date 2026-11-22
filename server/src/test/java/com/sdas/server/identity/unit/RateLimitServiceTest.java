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
import com.sdas.server.identity.service.RateLimitService;
import com.sdas.server.identity.service.impl.RateLimitServiceImpl;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * T031 前置单测：60 s 间隔、单日 10 次、15 分钟窗口、TTL 边界、Redis 异常降级。
 */
@SuppressWarnings("unchecked")
class RateLimitServiceTest {

    private ValueOperations<String, String> operations;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        IdentityProperties properties = mock(IdentityProperties.class);
        when(properties.verificationCode()).thenReturn(new IdentityProperties.VerificationCode(
                true, "888888", 300L, 60L, 10, 5));
        service = new RateLimitServiceImpl(redis, properties);
    }

    @Test
    void firstSendIsAllowed() {
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(operations.increment(anyString())).thenReturn(1L);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.checkSend("digest"));
    }

    @Test
    void sendWithin60SecondsIsRateLimited() {
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkSend("digest"));
        assertEquals(ErrorCode.RATE_LIMITED, exception.getErrorCode());
    }

    @Test
    void eleventhSendInOneDayIsRateLimited() {
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(operations.increment(anyString())).thenReturn(11L);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkSend("digest"));
        assertEquals(ErrorCode.RATE_LIMITED, exception.getErrorCode());
    }

    @Test
    void identityVerificationWindowIsRateLimited() {
        when(operations.increment(anyString())).thenReturn(21L);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkIdentityVerification("digest"));
        assertEquals(ErrorCode.RATE_LIMITED, exception.getErrorCode());
    }

    @Test
    void loginFailureWindowSkipsPasswordCheck() {
        when(operations.increment(anyString())).thenReturn(21L);
        org.junit.jupiter.api.Assertions.assertTrue(
                service.shouldSkipPasswordCheck("digest"));
    }

    @Test
    void redisFailureDegradesToServiceUnavailable() {
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkSend("digest"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
    }
}
