package com.sdas.server.health.service;

import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.Result;
import com.sdas.server.health.model.ComponentStatus;
import com.sdas.server.health.model.HealthReport;
import com.sdas.server.health.model.HealthStatus;
import com.sdas.server.health.repository.DatabaseProbeRepository;
import com.sdas.server.health.repository.RedisProbeRepository;
import com.sdas.server.health.service.impl.HealthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * HealthServiceImpl 单元测试（JUnit5 + Mockito）。
 *
 * <p>Mock 数据来源：specs/health-check/contracts/health-api.md M1/M2；
 * 枚举来源：specs/health-check/data-model.md。</p>
 */
@ExtendWith(MockitoExtension.class)
class HealthServiceImplTest {

    @Mock
    private DatabaseProbeRepository databaseProbeRepository;

    @Mock
    private RedisProbeRepository redisProbeRepository;

    private HealthService healthService;

    @BeforeEach
    void setUp() {
        healthService = new HealthServiceImpl(databaseProbeRepository, redisProbeRepository);
    }

    @Test
    void checkReturnsUpWhenAllProbesSucceed() {
        when(databaseProbeRepository.selectOne()).thenReturn(1);
        when(redisProbeRepository.ping()).thenReturn("PONG");

        Result<HealthReport> result = healthService.check();

        assertEquals(200, result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMessage(), result.getMessage());
        assertNotNull(result.getData());
        assertEquals(HealthStatus.UP, result.getData().getStatus());
        assertNotNull(result.getData().getCheckedAt());
        assertEquals(3, result.getData().getComponents().size());
        assertEquals("server", result.getData().getComponents().get(0).getName());
        assertEquals("database", result.getData().getComponents().get(1).getName());
        assertEquals("redis", result.getData().getComponents().get(2).getName());
        assertEquals("SELECT 1 = 1", component(result, "database").getDetail());
        assertEquals("PING = PONG", component(result, "redis").getDetail());
    }

    @Test
    void checkReturnsDownWhenDatabaseFails() {
        when(databaseProbeRepository.selectOne()).thenThrow(new IllegalStateException("connection refused"));
        when(redisProbeRepository.ping()).thenReturn("PONG");

        Result<HealthReport> result = healthService.check();

        assertEquals(503, result.getCode());
        assertEquals(ErrorCode.HEALTH_UNAVAILABLE.getMessage(), result.getMessage());
        assertEquals(HealthStatus.DOWN, result.getData().getStatus());
        assertEquals(HealthStatus.DOWN, component(result, "database").getStatus());
        assertEquals("unavailable", component(result, "database").getDetail());
        assertEquals(HealthStatus.UP, component(result, "redis").getStatus());
    }

    @Test
    void checkReturnsDownWhenRedisFails() {
        when(databaseProbeRepository.selectOne()).thenReturn(1);
        when(redisProbeRepository.ping()).thenThrow(new IllegalStateException("connection refused"));

        Result<HealthReport> result = healthService.check();

        assertEquals(503, result.getCode());
        assertEquals(HealthStatus.UP, component(result, "database").getStatus());
        assertEquals(HealthStatus.DOWN, component(result, "redis").getStatus());
    }

    @Test
    void checkReturnsDownWhenAllProbesFails() {
        when(databaseProbeRepository.selectOne()).thenThrow(new IllegalStateException("db down"));
        when(redisProbeRepository.ping()).thenThrow(new IllegalStateException("redis down"));

        Result<HealthReport> result = healthService.check();

        assertEquals(503, result.getCode());
        assertEquals(HealthStatus.UP, component(result, "server").getStatus());
        assertEquals(HealthStatus.DOWN, component(result, "database").getStatus());
        assertEquals(HealthStatus.DOWN, component(result, "redis").getStatus());
    }

    @Test
    void checkRejectsUnexpectedProbeValues() {
        when(databaseProbeRepository.selectOne()).thenReturn(0);
        when(redisProbeRepository.ping()).thenReturn("PONG");

        Result<HealthReport> result = healthService.check();

        assertEquals(503, result.getCode());
        assertEquals(HealthStatus.DOWN, component(result, "database").getStatus());
    }

    private ComponentStatus component(Result<HealthReport> result, String name) {
        return result.getData().getComponents().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseThrow();
    }
}
