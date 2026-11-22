package com.sdas.server.doctor.support;

import java.util.List;

/**
 * 解析成功后的结构化内容。
 *
 * @param summary   病情摘要
 * @param suggestions 诊断建议（已截断至上限）
 * @param medications 原始用药条目（尚未映射目录）
 */
public record ParsedDecision(
        DecisionSummary summary,
        List<DiagnosisSuggestion> suggestions,
        List<RawMedication> medications) {
}
