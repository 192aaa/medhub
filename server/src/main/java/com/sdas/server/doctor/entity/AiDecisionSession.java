package com.sdas.server.doctor.entity;

import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.model.TreatmentDecisionStatus;
import com.sdas.server.doctor.model.TreatmentTriggerType;
import java.time.LocalDateTime;

/**
 * AI 辅助决策会话实体（可追溯）。
 *
 * <p>表结构来源：specs/003-doctor-consultation/data-model.md 2.7（基础列）与
 * specs/006-ai-treatment-decision/data-model.md 2.1（006 扩展列）。</p>
 *
 * <p><b>006 兼容改造</b>：{@code status} 由 002 的 {@code TriageResultStatus} 改为
 * {@code TreatmentDecisionStatus}——后者包含 006 需要的 {@code ERROR_FALLBACK} 与
 * {@code EMPTY_FALLBACK}；两者共有的 {@code SUCCESS}/{@code TIMEOUT_FALLBACK} 字面值相同，
 * 因此既有数据与 003 端点 9 的响应字符串保持不变。</p>
 *
 * <p>新增组件<b>追加在末尾</b>，使 003 的既有构造调用只需补默认值。</p>
 *
 * @param id               主键
 * @param consultationId   关联问诊单
 * @param doctorId         请求医生
 * @param inputSummary     输入摘要（脱敏）
 * @param output           AI 输出（建议文本拼接，兼容 003）
 * @param modelUsed        模型名
 * @param status           结果状态
 * @param elapsedMs        耗时
 * @param createdAt        创建时间
 * @param triggerType      触发方式（006）
 * @param dialogVersion    对话指纹（006，幂等）
 * @param resultJson       结构化结果（006）
 * @param degradedReason   降级原因（006）
 * @param appliedItemCount 采纳条数（006）
 * @param adoptedAt        采纳时间（006）
 */
public record AiDecisionSession(
        Long id,
        Long consultationId,
        Long doctorId,
        String inputSummary,
        String output,
        String modelUsed,
        TreatmentDecisionStatus status,
        Integer elapsedMs,
        LocalDateTime createdAt,
        TreatmentTriggerType triggerType,
        String dialogVersion,
        String resultJson,
        DegradedReason degradedReason,
        Integer appliedItemCount,
        LocalDateTime adoptedAt) {

    /**
     * 003 兼容构造：补齐 006 扩展字段的默认值。
     *
     * @param id             主键
     * @param consultationId 问诊单
     * @param doctorId       医生
     * @param inputSummary   输入摘要
     * @param output         输出
     * @param modelUsed      模型
     * @param status         状态
     * @param elapsedMs      耗时
     * @param createdAt      创建时间
     */
    public AiDecisionSession(Long id, Long consultationId, Long doctorId, String inputSummary,
                             String output, String modelUsed, TreatmentDecisionStatus status,
                             Integer elapsedMs, LocalDateTime createdAt) {
        this(id, consultationId, doctorId, inputSummary, output, modelUsed, status, elapsedMs,
                createdAt, TreatmentTriggerType.AUTO, null, null, DegradedReason.NONE, null, null);
    }
}
