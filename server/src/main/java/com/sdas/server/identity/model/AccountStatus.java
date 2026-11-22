package com.sdas.server.identity.model;

/**
 * 账户可用状态。
 *
 * <p>唯一来源：specs/001-patient-identity/data-model.md 1.4。</p>
 */
public enum AccountStatus {
    /** 正常。 */
    ACTIVE,
    /** 已停用（本功能不提供停用入口）。 */
    DISABLED
}
