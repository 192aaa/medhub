package com.sdas.server.pharmacy.model;

/**
 * 处方取药状态（派生，不落库）。
 *
 * <p>唯一来源：specs/004-prescription-dispensing/data-model.md 1.2；
 * 判定优先级由 {@link com.sdas.server.pharmacy.service.DispenseStatusResolver} 实现。</p>
 */
public enum DispenseStatus {
    /** 已签名、未过期、未作废、无核销记录。 */
    PENDING,
    /** 存在核销记录（无论是否已过有效期）。 */
    DISPENSED,
    /** 无核销记录且当前时间晚于 valid_until。 */
    EXPIRED,
    /** 处方状态为 REVOKED。 */
    REVOKED
}
