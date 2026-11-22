package com.sdas.server.appointment.model;

/** 挂号单状态枚举（唯一来源：specs/002-appointment-consultation/data-model.md 1.1）。 */
public enum AppointmentStatus {
    PENDING_PAYMENT,
    PAID,
    CANCELLED,
    CLOSED
}
