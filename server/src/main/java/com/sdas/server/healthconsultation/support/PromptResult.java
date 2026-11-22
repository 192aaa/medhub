package com.sdas.server.healthconsultation.support;

/**
 * Prompt 组装结果。
 *
 * @param command          模型调用入参
 * @param contextTruncated 病历上下文是否因预算被截断（用于 {@code done} 事件）
 */
public record PromptResult(HealthChatCommand command, boolean contextTruncated) {
}
