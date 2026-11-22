package com.sdas.server.healthconsultation.dto;

import java.time.LocalDateTime;

/**
 * 新建会话响应（契约端点 2）。
 *
 * @param sessionId 会话主键
 * @param title     标题（默认「新对话」）
 * @param createdAt 创建时间
 */
public record SessionCreateResponse(long sessionId, String title, LocalDateTime createdAt) {
}
