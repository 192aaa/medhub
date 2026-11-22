package com.sdas.server.identity.entity;

import com.sdas.server.identity.model.AuditEventType;
import java.time.LocalDateTime;

/**
 * 安全审计事件实体。
 *
 * <p>字段唯一来源：specs/001-patient-identity/data-model.md 3.6；
 * 该表只有 occurred_at，无 created_at/updated_at/version 通用列，故本实体不含 version。
 * 禁止列入密码、验证码、完整手机号、完整证件号与 JWT 全文。</p>
 *
 * @param id              主键
 * @param eventType       事件类型
 * @param result          SUCCESS / FAILURE / REJECTED
 * @param actorPatientId  关联患者（注册失败时为空）
 * @param maskedPhone     脱敏手机号
 * @param maskedIdCard    脱敏证件号
 * @param reasonCode      内部原因码，不返回客户端
 * @param traceId        与 AccessLogFilter 对齐
 * @param occurredAt     事件时间
 */
public record SecurityAuditEvent(
        Long id,
        AuditEventType eventType,
        String result,
        Long actorPatientId,
        String maskedPhone,
        String maskedIdCard,
        String reasonCode,
        String traceId,
        LocalDateTime occurredAt) {
}
