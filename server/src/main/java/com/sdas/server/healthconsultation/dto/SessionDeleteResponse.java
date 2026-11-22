package com.sdas.server.healthconsultation.dto;

/**
 * 删除会话响应（契约端点 4）。
 *
 * @param sessionId 会话主键
 * @param deleted   是否已删除
 */
public record SessionDeleteResponse(long sessionId, boolean deleted) {
}
