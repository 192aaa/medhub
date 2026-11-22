package com.sdas.server.identity.model;

/**
 * 会话状态。
 *
 * <p>唯一来源：specs/001-patient-identity/data-model.md 1.5。</p>
 */
public enum SessionStatus {
    /** 有效。 */
    ACTIVE,
    /** 已撤销。 */
    REVOKED,
    /** 已过期。 */
    EXPIRED
}
