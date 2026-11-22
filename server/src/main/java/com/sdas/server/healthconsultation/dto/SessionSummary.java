package com.sdas.server.healthconsultation.dto;

import java.time.LocalDateTime;

/**
 * 会话列表项（契约端点 1）。
 *
 * @param sessionId          会话主键
 * @param title              标题
 * @param lastActiveAt       最近活跃时间（倒序排序键）
 * @param messageCount       消息条数
 * @param lastMessagePreview 最后一条消息前 40 字（无消息为 null）
 */
public record SessionSummary(
        long sessionId,
        String title,
        LocalDateTime lastActiveAt,
        int messageCount,
        String lastMessagePreview) {
}
