package com.sdas.server.healthconsultation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 流式问答请求。
 *
 * <p>契约：{@code POST /api/v1/health-consultation/sessions/{sessionId}/messages}，
 * {@code content} 长度上限由 {@code sdas.ai.health.message-max-chars} 在 Service 层校验
 * （超限返回 {@code MESSAGE_TOO_LONG}）。</p>
 *
 * @param content 患者输入
 */
public record ChatRequest(@NotBlank String content) {
}
