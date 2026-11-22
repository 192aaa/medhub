package com.sdas.server.doctor.dto;

import java.util.List;

/**
 * 结构化诊断建议（端点 1/2/3 响应 suggestions[]）。
 *
 * <p>{@code diagnosis} 已由服务端做确定性措辞归一化；{@code basis} 至少 1 条。</p>
 *
 * @param diagnosis  建议诊断（禁止确定性措辞）
 * @param likelihood 可能性等级 HIGH/MEDIUM/LOW
 * @param basis      依据
 * @param workup     建议检查
 * @param riskAlert  风险提示（可为 null）
 */
public record DiagnosisSuggestionView(
        String diagnosis,
        String likelihood,
        List<String> basis,
        List<String> workup,
        String riskAlert) {
}
