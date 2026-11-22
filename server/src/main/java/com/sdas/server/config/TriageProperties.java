package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 智能导诊配置（prefix = sdas.ai.triage）。
 *
 * <p>取值来源：specs/002-appointment-consultation/data-model.md 第 3 节。
 * timeoutMillis 默认 5000：超时即降级为全量科室列表（HTTP 200，SC-006）。</p>
 *
 * @param timeoutMillis  AI 调用超时（毫秒）
 * @param fallback       降级策略（FULL_DEPARTMENT_LIST）
 * @param model          AI 模型名（复用 DeepSeek 官方 API）
 */
@ConfigurationProperties(prefix = "sdas.ai.triage")
public record TriageProperties(long timeoutMillis, String fallback, String model) {
}
