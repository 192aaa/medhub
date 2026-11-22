package com.sdas.server.doctor.support;

import java.util.List;

/**
 * 解析后的诊断建议（已做确定性措辞归一化）。
 *
 * @param diagnosis  建议诊断（以「疑似」开头或中性表述）
 * @param likelihood 可能性等级 HIGH/MEDIUM/LOW
 * @param basis      依据（至少 1 条）
 * @param workup     建议检查
 * @param riskAlert  风险提示（可为 null）
 */
public record DiagnosisSuggestion(
        String diagnosis,
        String likelihood,
        List<String> basis,
        List<String> workup,
        String riskAlert) {
}
