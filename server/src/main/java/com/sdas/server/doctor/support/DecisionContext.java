package com.sdas.server.doctor.support;

import com.sdas.server.healthconsultation.support.KnowledgeHit;
import java.util.List;

/**
 * 治疗决策上下文（plan.md M2，只读聚合，不落库）。
 *
 * @param chiefComplaint  主诉（截断 500）
 * @param description     描述（截断 500）
 * @param dialog          对话窗口（正序、最近优先裁剪）
 * @param imageCount      图片数量（不读二进制）
 * @param evidenceLimited 依据是否有限（服务端判定，不由模型自述）
 * @param knowledge       知识依据（同科室优先）
 */
public record DecisionContext(
        String chiefComplaint,
        String description,
        List<ChatTurn> dialog,
        int imageCount,
        boolean evidenceLimited,
        List<KnowledgeHit> knowledge) {
}
