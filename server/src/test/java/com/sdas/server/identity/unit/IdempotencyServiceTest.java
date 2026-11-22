package com.sdas.server.identity.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.identity.service.IdempotencyService;
import com.sdas.server.identity.service.impl.IdempotencyServiceImpl;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * T032 前置单测：首次写入、重复命中、TTL 过期后重新执行。
 */
@SuppressWarnings("unchecked")
class IdempotencyServiceTest {

    private ValueOperations<String, String> operations;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        service = new IdempotencyServiceImpl(redis, new ObjectMapper());
    }

    @Test
    void firstCallExecutesActionAndStoresSnapshot() {
        AtomicInteger calls = new AtomicInteger();
        String result = service.execute("key", String.class,
                () -> "result-" + calls.incrementAndGet());
        assertEquals("result-1", result);
        assertEquals(1, calls.get());
        verify(operations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void repeatedCallReturnsSnapshotWithoutExecutingAction() {
        when(operations.get(anyString())).thenReturn("\"cached\"");
        AtomicInteger calls = new AtomicInteger();
        String result = service.execute("key", String.class,
                () -> "result-" + calls.incrementAndGet());
        assertEquals("cached", result);
        assertEquals(0, calls.get());
        verify(operations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void expiredSnapshotReexecutesAction() {
        when(operations.get(anyString())).thenReturn(null);
        assertEquals("fresh", service.execute("key", String.class, () -> "fresh"));
    }
}
