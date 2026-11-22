package com.sdas.server.doctor.support;

import com.sdas.server.doctor.dto.CaseSummaryView;
import com.sdas.server.doctor.dto.DiagnosisSuggestionView;
import com.sdas.server.doctor.dto.MedicationDraftView;
import com.sdas.server.doctor.model.DegradedReason;
import java.util.List;

/**
 * {@code ai_decision_session.result_json} 的结构（data-model.md 7.3）。
 *
 * <p>只存结构化结果与 {@code degradedReason}，不含提示词原文、患者身份信息。</p>
 *
 * @param summary            病情摘要（降级为 null）
 * @param suggestions        诊断建议
 * @param medicationDrafts   用药草稿
 * @param ignoredMedications 目录外药名
 * @param degradedReason     降级原因
 */
public record DecisionResult(
        CaseSummaryView summary,
        List<DiagnosisSuggestionView> suggestions,
        List<MedicationDraftView> medicationDrafts,
        List<String> ignoredMedications,
        DegradedReason degradedReason) {

    /**
     * 构造降级结果（摘要为空、数组为空）。
     *
     * @param reason 降级原因
     * @return 降级结果
     */
    public static DecisionResult degraded(DegradedReason reason) {
        return new DecisionResult(null, List.of(), List.of(), List.of(), reason);
    }
}
