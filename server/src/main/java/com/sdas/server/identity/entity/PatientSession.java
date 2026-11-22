package com.sdas.server.identity.entity;

import com.sdas.server.identity.model.SessionStatus;
import java.time.LocalDateTime;

/**
 * 会话与续期凭证实体。
 *
 * <p>字段唯一来源：specs/001-patient-identity/data-model.md 3.4。</p>
 *
 * @param id                 会话主键（写入 JWT sessionId）
 * @param patientId          患者主键
 * @param refreshTokenHash   续期凭证摘要
 * @param sessionStatus      会话状态
 * @param clientIpHash       客户端 IP 摘要
 * @param expiresAt          到期时间
 * @param revokedAt          撤销时间
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 * @param version            乐观锁版本
 */
public record PatientSession(
        Long id,
        Long patientId,
        String refreshTokenHash,
        SessionStatus sessionStatus,
        String clientIpHash,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
