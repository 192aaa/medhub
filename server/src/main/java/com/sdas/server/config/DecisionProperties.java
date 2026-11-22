package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 辅助决策配置（prefix = sdas.ai.decision）。
 *
 * <p>取值唯一来源：specs/003-doctor-consultation/data-model.md 第 3 节。
 * timeoutMillis 默认 5000：超时降级为空建议（HTTP 200，SC-009）。</p>
 *
 * @param timeoutMillis AI 调用超时（毫秒）
 * @param model         AI 模型名（复用 002 DeepSeek/SophNet）
 */
@ConfigurationProperties(prefix = "sdas.ai.decision")
public record DecisionProperties(long timeoutMillis, String model) {
}
