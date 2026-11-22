package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI健康咨询配置（prefix = sdas.ai.health）。
 *
 * <p>取值来源：specs/005-ai-health-consultation/data-model.md 第 6 节。
 * 超时语义：{@code firstTokenTimeoutMillis} 为流式首包/idle 超时（SC-001），
 * {@code timeoutMillis} 为单轮整体截止。</p>
 *
 * @param model                   模型名（R-01）
 * @param timeoutMillis           单轮总超时
 * @param firstTokenTimeoutMillis 首包超时
 * @param maxOutputTokens         单轮最大输出 token
 * @param contextMaxChars         prompt 总预算
 * @param contextMedicalMaxChars  病历摘要预算（R-04）
 * @param historyWindow           热窗口条数
 * @param messageMaxChars         患者单条输入上限
 * @param sessionMessageLimit     单会话消息上限
 * @param knowledgeTopK           知识检索条数
 */
@ConfigurationProperties(prefix = "sdas.ai.health")
public record HealthProperties(
        String model,
        long timeoutMillis,
        long firstTokenTimeoutMillis,
        int maxOutputTokens,
        int contextMaxChars,
        int contextMedicalMaxChars,
        int historyWindow,
        int messageMaxChars,
        int sessionMessageLimit,
        int knowledgeTopK) {

    /** 默认构造值，供测试与缺省绑定使用。 */
    public HealthProperties {
        model = model == null || model.isBlank() ? "deepseek-v4-flash" : model;
        timeoutMillis = timeoutMillis <= 0 ? 8000L : timeoutMillis;
        firstTokenTimeoutMillis = firstTokenTimeoutMillis <= 0 ? 3000L : firstTokenTimeoutMillis;
        maxOutputTokens = maxOutputTokens <= 0 ? 1024 : maxOutputTokens;
        contextMaxChars = contextMaxChars <= 0 ? 6000 : contextMaxChars;
        contextMedicalMaxChars = contextMedicalMaxChars <= 0 ? 1500 : contextMedicalMaxChars;
        historyWindow = historyWindow <= 0 ? 12 : historyWindow;
        messageMaxChars = messageMaxChars <= 0 ? 2000 : messageMaxChars;
        sessionMessageLimit = sessionMessageLimit <= 0 ? 200 : sessionMessageLimit;
        knowledgeTopK = knowledgeTopK <= 0 ? 5 : knowledgeTopK;
    }
}
