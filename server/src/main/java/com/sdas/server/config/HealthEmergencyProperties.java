package com.sdas.server.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 紧急症状规则配置（prefix = sdas.ai.health.emergency）。
 *
 * <p>取值来源：specs/005-ai-health-consultation/plan.md M5。
 * {@code rules} 为空时<b>整体启用内置默认表</b>，不得视为「关闭预警」（安全优先）。</p>
 *
 * @param rules 规则列表；为空则使用 {@link DefaultEmergencyRules#RULES}
 */
@ConfigurationProperties(prefix = "sdas.ai.health.emergency")
public record HealthEmergencyProperties(List<EmergencyRule> rules) {

    /**
     * 生效规则：配置非空则整体覆盖，否则回落内置默认表。
     *
     * @return 规则列表（不为 null）
     */
    public List<EmergencyRule> effectiveRules() {
        return rules == null || rules.isEmpty() ? DefaultEmergencyRules.RULES : rules;
    }
}
