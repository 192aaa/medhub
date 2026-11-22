package com.sdas.server.doctor.model;

/**
 * 辅助生成结果状态（specs/006-ai-treatment-decision/data-model.md 1.1）。
 *
 * <p>降级仍返回 HTTP 200，由本枚举表达失败原因类别；未知值按
 * {@code EMPTY_FALLBACK} 处理（安全优先）。</p>
 */
public enum TreatmentDecisionStatus {

    /** 生成成功且结构完整。 */
    SUCCESS,

    /** 模型超时。 */
    TIMEOUT_FALLBACK,

    /** 上游异常/网络错误。 */
    ERROR_FALLBACK,

    /** 返回空或结构不可解析。 */
    EMPTY_FALLBACK
}
