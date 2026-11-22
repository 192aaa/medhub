package com.sdas.server.identity.repository;

import com.sdas.server.identity.entity.SecurityAuditEvent;
import com.sdas.server.identity.model.converter.AuditEventTypeConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 安全审计事件持久化；字段严格限定 data-model.md 3.6。
 *
 * <p>禁止写入密码、验证码、完整手机号、完整证件号与 JWT 全文。</p>
 */
@Repository
public class SecurityAuditEventRepository {

    private static final AuditEventTypeConverter CONVERTER = new AuditEventTypeConverter();

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造审计事件仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public SecurityAuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入审计事件。
     *
     * @param event 审计事件
     */
    public void insert(SecurityAuditEvent event) {
        jdbcTemplate.update(
                "INSERT INTO security_audit_event (event_type, result, actor_patient_id, "
                        + "masked_phone, masked_id_card, reason_code, trace_id, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                CONVERTER.convertToDatabaseColumn(event.eventType()), event.result(),
                event.actorPatientId(), event.maskedPhone(), event.maskedIdCard(),
                event.reasonCode(), event.traceId(),
                java.sql.Timestamp.valueOf(event.occurredAt()));
    }
}
