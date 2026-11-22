package com.sdas.server.identity.model;

/**
 * 验证码生命周期状态（存 Redis，不建 MySQL 表）。
 *
 * <p>唯一来源：specs/001-patient-identity/data-model.md 1.3；EXPIRED / REVOKED 不得出现在响应中。</p>
 */
public enum VerificationCodeStatus {
    /** 已签发未使用。 */
    ACTIVE,
    /** 已消费。 */
    USED,
    /** 已过期（仅审计语义）。 */
    EXPIRED,
    /** 已作废。 */
    REVOKED
}
