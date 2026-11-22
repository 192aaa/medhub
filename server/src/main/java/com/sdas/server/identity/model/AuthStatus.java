package com.sdas.server.identity.model;

/**
 * 患者实名认证状态。
 *
 * <p>唯一来源：specs/001-patient-identity/data-model.md 1.1；单向流转 UNVERIFIED → VERIFIED。</p>
 */
public enum AuthStatus {
    /** 未认证。 */
    UNVERIFIED,
    /** 已认证。 */
    VERIFIED
}
