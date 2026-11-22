package com.sdas.server.health.controller;

import com.sdas.server.health.repository.DatabaseProbeRepository;
import com.sdas.server.health.repository.RedisProbeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/health 集成测试（Spring Boot Test）。
 *
 * <p>宪法原则 VI：真实后端 E2E 必须断言响应 code 值；Mock 数据来自
 * specs/health-check/contracts/health-api.md M1–M3，禁止凭空手写。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerIntegrationTest {

    private static final String ISO_8601 = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseProbeRepository databaseProbeRepository;

    @MockitoBean
    private RedisProbeRepository redisProbeRepository;

    @Test
    void healthReturnsCode200WhenAllComponentsUp() throws Exception {
        when(databaseProbeRepository.selectOne()).thenReturn(1);
        when(redisProbeRepository.ping()).thenReturn("PONG");

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.components.length()").value(3))
                .andExpect(jsonPath("$.data.components[0].name").value("server"))
                .andExpect(jsonPath("$.data.components[1].name").value("database"))
                .andExpect(jsonPath("$.data.components[2].name").value("redis"))
                .andExpect(jsonPath("$.data.checkedAt", matchesPattern(ISO_8601)));
    }

    @Test
    void healthReturnsCode503WhenDatabaseDown() throws Exception {
        when(databaseProbeRepository.selectOne()).thenThrow(new IllegalStateException("db down"));
        when(redisProbeRepository.ping()).thenReturn("PONG");

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("依赖组件不可用"))
                .andExpect(jsonPath("$.data.status").value("DOWN"))
                .andExpect(jsonPath("$.data.components[1].status").value("DOWN"))
                .andExpect(jsonPath("$.data.components[1].detail").value("unavailable"))
                .andExpect(jsonPath("$.data.checkedAt", matchesPattern(ISO_8601)));
    }

    @Test
    void postHealthReturnsCode405() throws Exception {
        mockMvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.message").value("请求方法不支持"));
    }
}
