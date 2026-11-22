package com.sdas.server.appointment.support;

import com.sdas.server.appointment.service.SummaryClient;
import com.sdas.server.appointment.service.TriageClient;
import com.sdas.server.identity.support.IdentityTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 002 契约 / 集成测试基类。
 *
 * <p>复用 001 的 {@link IdentityTestSupport}（Flyway 强制迁移 + MockMvc +
 * {@code IDENTITY_TEST_USE_LOCAL_INFRA} 本机基础设施开关）；AI 依赖用
 * {@link MockitoBean} 替换，避免真实外呼与 5–8 秒阻塞。</p>
 */
public abstract class AppointmentTestSupport extends IdentityTestSupport {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected SummaryClient summaryClient;

    @MockitoBean
    protected TriageClient triageClient;
}
