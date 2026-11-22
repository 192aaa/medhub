package com.sdas.server.identity.model;

/**
 * 验证码用途，参与 Redis key 与用途隔离校验。
 *
 * <p>唯一来源：specs/001-patient-identity/data-model.md 1.2。</p>
 */
public enum VerificationPurpose {
    /** 注册。 */
    REGISTER,
    /** 重置密码。 */
    RESET_PASSWORD
}
