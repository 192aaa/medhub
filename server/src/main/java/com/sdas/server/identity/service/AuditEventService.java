package com.sdas.server.identity.service;

import com.sdas.server.identity.model.AuditEventType;

/**
 * 统一审计事件写入入口（10 类 AuditEventType）。
 */
public interface AuditEventService {

    /**
     * 记录审计事件，写入失败不得影响主流程。
     *
     * @param eventType      事件类型
     * @param result         SUCCESS / FAILURE / REJECTED
     * @param actorPatientId 关联患者，可为空
     * @param maskedPhone    脱敏手机号，禁止明文
     * @param maskedIdCard   脱敏证件号，禁止明文
     * @param reasonCode     内部原因码，不返回客户端
     */
    void record(AuditEventType eventType, String result, Long actorPatientId,
                String maskedPhone, String maskedIdCard, String reasonCode);
}
