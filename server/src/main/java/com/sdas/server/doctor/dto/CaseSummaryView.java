package com.sdas.server.doctor.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 病情摘要（端点 1/2/3 响应 summary）。
 *
 * <p>字段来源：specs/006-ai-treatment-decision/data-model.md 7.2 与
 * contracts/ai-treatment-decision-api.md 端点 1。</p>
 *
 * @param chiefComplaint    主诉
 * @param presentIllness    现病史
 * @param dialogHighlights  对话要点
 * @param riskAlerts        风险提示
 * @param evidenceLimited   依据是否有限（服务端判定）
 * @param generatedAt       摘要生成时间（复用时为首次生成时间）
 * @param modelUsed         模型名
 */
public record CaseSummaryView(
        String chiefComplaint,
        String presentIllness,
        List<String> dialogHighlights,
        List<String> riskAlerts,
        boolean evidenceLimited,
        LocalDateTime generatedAt,
        String modelUsed) {
}
