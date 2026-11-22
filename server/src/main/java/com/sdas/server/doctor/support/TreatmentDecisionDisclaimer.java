package com.sdas.server.doctor.support;

/**
 * 治疗决策辅助免责文案（唯一来源，FR-024）。
 *
 * <p>服务端固定下发，前端只渲染、禁止自行拼接。与 003 端点 9 的
 * {@code AiDecisionServiceImpl.DISCLAIMER} 并存：后者服务于自由文本建议，
 * 本常量服务于结构化治疗决策。</p>
 */
public final class TreatmentDecisionDisclaimer {

    /** 固定文案（逐字对齐 contracts/ai-treatment-decision-api.md）。 */
    public static final String TEXT = "AI 生成，仅供参考，最终诊疗决策由医生负责";

    private TreatmentDecisionDisclaimer() {
    }
}
