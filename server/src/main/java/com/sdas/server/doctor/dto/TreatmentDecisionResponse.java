package com.sdas.server.doctor.dto;

import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.model.TreatmentDecisionStatus;
import com.sdas.server.doctor.model.TreatmentTriggerType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 端点 1/2/3 共用响应 data。
 *
 * <p>字段与取值来源：specs/006-ai-treatment-decision/data-model.md 7.2 与
 * contracts/ai-treatment-decision-api.md。降级时 {@code summary=null}、
 * 两个数组为空、{@code disclaimer} 照常下发。</p>
 *
 * @param decisionId         留痕主键
 * @param consultationId     问诊单
 * @param status             结果状态
 * @param triggerType        触发方式
 * @param reused             是否复用既有结果（未调用模型）
 * @param summary            病情摘要（降级为 null）
 * @param suggestions        诊断建议
 * @param medicationDrafts   用药草稿
 * @param ignoredMedications 目录外被忽略的药名
 * @param disclaimer         固定免责文案
 * @param degradedReason     降级原因
 * @param elapsedMs          本次耗时（复用时 0）
 * @param generatedAt        记录创建时间
 */
public record TreatmentDecisionResponse(
        long decisionId,
        long consultationId,
        TreatmentDecisionStatus status,
        TreatmentTriggerType triggerType,
        boolean reused,
        CaseSummaryView summary,
        List<DiagnosisSuggestionView> suggestions,
        List<MedicationDraftView> medicationDrafts,
        List<String> ignoredMedications,
        String disclaimer,
        DegradedReason degradedReason,
        int elapsedMs,
        LocalDateTime generatedAt) {
}
