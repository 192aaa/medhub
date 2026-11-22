package com.sdas.server.doctor.model;

/**
 * 辅助生成触发方式（specs/006-ai-treatment-decision/data-model.md 1.2）。
 */
public enum TreatmentTriggerType {

    /** 进入开方页自动生成（含幂等复用）。 */
    AUTO,

    /** 医生手动重新生成。 */
    REGENERATE
}
