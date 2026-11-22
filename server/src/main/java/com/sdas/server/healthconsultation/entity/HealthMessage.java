package com.sdas.server.healthconsultation.entity;

import com.sdas.server.healthconsultation.model.MessageRole;
import com.sdas.server.healthconsultation.model.StreamStatus;
import java.time.LocalDateTime;

/**
 * 咨询消息实体。
 *
 * <p>表结构来源：specs/005-ai-health-consultation/data-model.md 2.2。
 * {@code content} 不含免责声明，声明单独存 {@code disclaimer} 列。</p>
 *
 * @param id           主键
 * @param sessionId    所属会话
 * @param role         消息角色
 * @param content      正文（患者输入或模型正文）
 * @param disclaimer   免责声明（仅助手消息）
 * @param emergency    本条是否触发预警
 * @param ruleCode     命中的规则码（可解释性）
 * @param streamStatus 流式完成状态
 * @param modelUsed    实际模型名
 * @param createdAt    创建时间
 */
public record HealthMessage(
        long id,
        long sessionId,
        MessageRole role,
        String content,
        String disclaimer,
        boolean emergency,
        String ruleCode,
        StreamStatus streamStatus,
        String modelUsed,
        LocalDateTime createdAt) {
}
