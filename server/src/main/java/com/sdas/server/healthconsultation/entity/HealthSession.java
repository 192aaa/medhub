package com.sdas.server.healthconsultation.entity;

import com.sdas.server.healthconsultation.model.SessionStatus;
import java.time.LocalDateTime;

/**
 * 咨询会话实体。
 *
 * <p>表结构来源：specs/005-ai-health-consultation/data-model.md 2.1。
 * {@code status} 由 {@code SessionStatusConverter} 转换。</p>
 *
 * @param id           主键
 * @param patientId    归属患者（一切查询强制带此条件）
 * @param title        标题（默认取首条患者消息前 20 字）
 * @param lastActiveAt 最近活跃时间（列表排序键）
 * @param messageCount 消息条数冗余计数
 * @param status       会话状态
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @param version      乐观锁版本
 */
public record HealthSession(
        long id,
        long patientId,
        String title,
        LocalDateTime lastActiveAt,
        int messageCount,
        SessionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
