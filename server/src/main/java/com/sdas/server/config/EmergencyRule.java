package com.sdas.server.config;

import java.util.List;

/**
 * 紧急症状规则（{@code sdas.ai.health.emergency.rules[]}）。
 *
 * <p>字段说明见 specs/005-ai-health-consultation/plan.md M5 与 data-model.md 第 6 节。
 * 注意：{@code department} 表无 code 列且 005 只读依赖其结构，故预填科室按
 * {@link #departmentName()} 精确匹配 {@code department.name}。</p>
 *
 * @param code           规则标识（留痕用，如 CHEST_PAIN_DYSPNEA）
 * @param name           规则名称（预警标题可复用）
 * @param level          级别：HIGH / MEDIUM / LOW
 * @param departmentName 建议科室名称（匹配 department.name）
 * @param keywords       触发关键词
 */
public record EmergencyRule(
        String code,
        String name,
        String level,
        String departmentName,
        List<String> keywords) {

    /**
     * 级别权重，用于多规则命中时取最高。
     *
     * @return 权重（HIGH=3 / MEDIUM=2 / 其它=1）
     */
    public int levelWeight() {
        if ("HIGH".equalsIgnoreCase(level)) {
            return 3;
        }
        if ("MEDIUM".equalsIgnoreCase(level)) {
            return 2;
        }
        return 1;
    }
}
