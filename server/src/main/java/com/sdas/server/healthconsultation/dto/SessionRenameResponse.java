package com.sdas.server.healthconsultation.dto;

/**
 * 重命名会话响应（契约端点 3）。
 *
 * @param sessionId 会话主键
 * @param title     新标题
 */
public record SessionRenameResponse(long sessionId, String title) {
}
