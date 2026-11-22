package com.sdas.server.appointment.model;

/** 支付流水状态枚举（唯一来源：specs/002-appointment-consultation/data-model.md 1.3）。 */
public enum PaymentRecordStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED
}
