package com.sdas.server.appointment.model;

/** 支付状态枚举（唯一来源：specs/002-appointment-consultation/data-model.md 1.2）。 */
public enum PaymentStatus {
    UNPAID,
    PAID,
    REFUNDED,
    CLOSED
}
