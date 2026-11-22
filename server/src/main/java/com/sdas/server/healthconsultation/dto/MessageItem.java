package com.sdas.server.healthconsultation.dto;

import com.sdas.server.healthconsultation.model.MessageRole;
import com.sdas.server.healthconsultation.model.StreamStatus;
import java.time.LocalDateTime;

/**
 * 会话消息历史项（契约端点 6）。
 *
 * @param messageId    消息主键
 * @param role         角色
 * @param content      正文（助手消息不含免责声明）
 * @param disclaimer   免责声明（患者消息为 null）
 * @param emergency    是否触发预警
 * @param streamStatus 流式完成状态
 * @param createdAt    创建时间
 */
public record MessageItem(
        long messageId,
        MessageRole role,
        String content,
        String disclaimer,
        boolean emergency,
        StreamStatus streamStatus,
        LocalDateTime createdAt) {
}
