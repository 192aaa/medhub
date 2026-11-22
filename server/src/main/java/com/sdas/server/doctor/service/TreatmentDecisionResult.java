package com.sdas.server.doctor.service;

import com.sdas.server.doctor.model.DegradedReason;

/**
 * 治疗决策模型调用结果（plan.md M1）。
 *
 * <p>失败时只带 {@link DegradedReason}，不携带异常堆栈、不生成兜底文案。</p>
 *
 * @param success   是否成功拿到非空内容
 * @param rawJson   模型原始输出（未解析）
 * @param modelUsed 实际模型名
 * @param reason    失败原因（成功时为 {@code NONE}）
 */
public record TreatmentDecisionResult(
        boolean success,
        String rawJson,
        String modelUsed,
        DegradedReason reason) {

    /**
     * 构造成功结果。
     *
     * @param rawJson   模型原始输出
     * @param modelUsed 模型名
     * @return 成功结果
     */
    public static TreatmentDecisionResult success(String rawJson, String modelUsed) {
        return new TreatmentDecisionResult(true, rawJson, modelUsed, DegradedReason.NONE);
    }

    /**
     * 构造失败结果。
     *
     * @param reason 失败原因
     * @return 失败结果
     */
    public static TreatmentDecisionResult degraded(DegradedReason reason) {
        return new TreatmentDecisionResult(false, null, null, reason);
    }
}
