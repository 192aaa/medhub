package com.sdas.server.doctor.model;

/** 接诊超时状态枚举（唯一来源：specs/003-doctor-consultation/data-model.md 1.4）。 */
public enum ConsultationTimeoutStatus {
    NONE,
    ADMIN_NOTIFIED,
    TRANSFERRED
}
