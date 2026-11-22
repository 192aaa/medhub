package com.sdas.server.healthconsultation.model;

/**
 * 紧急判定结论。
 *
 * <p>枚举唯一来源：specs/005-ai-health-consultation/data-model.md 1.4；
 * 判定矩阵见 plan.md M5。{@link #UNAVAILABLE} 仍须预警（安全优先）。</p>
 */
public enum EmergencyVerdict {

    /** 规则命中且模型确认。 */
    CONFIRMED,

    /** 规则命中但模型否决（不预警，留痕）。 */
    REJECTED,

    /** 规则命中但模型复核不可用（仍预警）。 */
    UNAVAILABLE,

    /** 规则未命中、模型独立判定为紧急（预警）。 */
    MODEL_ONLY
}
