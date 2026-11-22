package com.sdas.server.doctor.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 端点 6 请求体（发送回复）。
 *
 * @param content 回复内容（1-1000 字符）
 */
public record MessageRequest(@NotBlank String content) {
}
