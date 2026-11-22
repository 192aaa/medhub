package com.sdas.server.doctor.support;

import java.util.List;

/**
 * 解析后的病情摘要文本（不含生成时间与模型名，由服务端补齐）。
 *
 * @param chiefComplaint   主诉
 * @param presentIllness   现病史
 * @param dialogHighlights 对话要点
 * @param riskAlerts       风险提示
 */
public record DecisionSummary(
        String chiefComplaint,
        String presentIllness,
        List<String> dialogHighlights,
        List<String> riskAlerts) {
}
