package com.sdas.server.doctor.model;

/** 处方状态枚举（唯一来源：specs/003-doctor-consultation/data-model.md 1.2）。 */
public enum PrescriptionStatus {
    DRAFT,
    SIGNED,
    EXPIRED,
    REVOKED
}
