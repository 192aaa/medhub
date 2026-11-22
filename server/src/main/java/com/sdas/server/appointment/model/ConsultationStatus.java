package com.sdas.server.appointment.model;

/** 图文问诊单状态枚举（唯一来源：specs/002-appointment-consultation/data-model.md 1.5，003 复用）。 */
public enum ConsultationStatus {
    PENDING,
    IN_PROGRESS,
    REPLIED,
    COMPLETED,
    CANCELLED
}
