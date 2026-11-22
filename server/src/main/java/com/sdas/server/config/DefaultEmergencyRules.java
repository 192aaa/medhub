package com.sdas.server.config;

import java.util.List;

/**
 * 内置紧急症状规则默认表（{@code sdas.ai.health.emergency.rules} 缺省时生效）。
 *
 * <p>来源：specs/005-ai-health-consultation/plan.md M5。配置非空时整体覆盖本表；
 * 本表不得为空，否则紧急预警链路失效（SC-003 召回 100%）。</p>
 */
public final class DefaultEmergencyRules {

    /** 默认规则（按声明顺序作为同级别时的稳定排序）。 */
    public static final List<EmergencyRule> RULES = List.of(
            new EmergencyRule("CHEST_PAIN_DYSPNEA", "胸痛伴呼吸困难", "HIGH", "心血管内科",
                    List.of("胸痛", "胸口疼", "胸闷", "喘不上气", "呼吸困难")),
            new EmergencyRule("STROKE_SIGNS", "卒中征象", "HIGH", "神经内科",
                    List.of("口角歪斜", "一侧无力", "说话不清", "言语不清", "突然倒地")),
            new EmergencyRule("SEVERE_BLEEDING", "大出血", "HIGH", "急诊科",
                    List.of("大出血", "止不住血", "呕血", "咯血")));

    private DefaultEmergencyRules() {
    }
}
