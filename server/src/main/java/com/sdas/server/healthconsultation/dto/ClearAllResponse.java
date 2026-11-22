package com.sdas.server.healthconsultation.dto;

/**
 * 清空全部历史响应（契约端点 5）。
 *
 * @param deletedCount 被删除的会话数
 */
public record ClearAllResponse(int deletedCount) {
}
