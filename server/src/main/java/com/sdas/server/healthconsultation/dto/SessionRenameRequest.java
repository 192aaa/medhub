package com.sdas.server.healthconsultation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 重命名会话请求（契约端点 3，1–64 字符，超限返回 {@code SESSION_TITLE_INVALID}）。
 *
 * @param title 新标题
 */
public record SessionRenameRequest(@NotBlank String title) {
}
