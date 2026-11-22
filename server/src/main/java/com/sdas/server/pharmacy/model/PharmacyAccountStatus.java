package com.sdas.server.pharmacy.model;

/**
 * 药店账号状态。
 *
 * <p>唯一来源：specs/004-prescription-dispensing/data-model.md 1.1。
 * 以字符串持久化，转换见 {@link com.sdas.server.pharmacy.model.converter.PharmacyAccountStatusConverter}。</p>
 */
public enum PharmacyAccountStatus {
    /** 正常可用。 */
    ACTIVE,
    /** 已禁用；登录失败统一模糊提示。 */
    DISABLED
}
