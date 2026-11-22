package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI辅助治疗决策配置（prefix = sdas.ai.treatment）。
 *
 * <p>取值来源：specs/006-ai-treatment-decision/data-model.md 第 6 节。
 * 在 {@link DoctorConfig} 中显式注册，与 003 的 {@code sdas.ai.decision.*} 并列，
 * 复用同一个 Spring AI {@code ChatClient.Builder}。</p>
 *
 * @param model                 模型名
 * @param timeoutMillis         单次生成整体超时
 * @param maxOutputTokens       输出 token 上限
 * @param dialogMaxMessages     读取对话条数上限
 * @param dialogMaxChars        对话字符预算（最近优先）
 * @param knowledgeTopK         知识检索条数
 * @param maxSuggestions        诊断建议条数上限
 * @param maxMedications        用药草稿条数上限
 * @param regenerateLimitPerHour 重新生成频控（次/小时/问诊单）
 * @param reuseWindowMinutes    幂等复用窗口（分钟）
 */
@ConfigurationProperties(prefix = "sdas.ai.treatment")
public record TreatmentDecisionProperties(
        String model,
        long timeoutMillis,
        int maxOutputTokens,
        int dialogMaxMessages,
        int dialogMaxChars,
        int knowledgeTopK,
        int maxSuggestions,
        int maxMedications,
        int regenerateLimitPerHour,
        int reuseWindowMinutes) {

    /** 缺省值与 data-model 第 6 节一致。 */
    public TreatmentDecisionProperties {
        model = model == null || model.isBlank() ? "DeepSeek-Flash-1017" : model;
        timeoutMillis = timeoutMillis <= 0 ? 15000L : timeoutMillis;
        maxOutputTokens = maxOutputTokens <= 0 ? 2048 : maxOutputTokens;
        dialogMaxMessages = dialogMaxMessages <= 0 ? 40 : dialogMaxMessages;
        dialogMaxChars = dialogMaxChars <= 0 ? 3000 : dialogMaxChars;
        knowledgeTopK = knowledgeTopK <= 0 ? 5 : knowledgeTopK;
        maxSuggestions = maxSuggestions <= 0 ? 5 : maxSuggestions;
        maxMedications = maxMedications <= 0 ? 10 : maxMedications;
        regenerateLimitPerHour = regenerateLimitPerHour <= 0 ? 3 : regenerateLimitPerHour;
        reuseWindowMinutes = reuseWindowMinutes <= 0 ? 30 : reuseWindowMinutes;
    }
}
