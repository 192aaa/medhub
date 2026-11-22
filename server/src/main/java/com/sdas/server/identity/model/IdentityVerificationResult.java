package com.sdas.server.identity.model;

/**
 * 实名认证结果，仅内部与审计使用，禁止出现在任何响应 DTO。
 *
 * <p>唯一来源：specs/001-patient-identity/data-model.md 1.7（FR-019、SC-005）。</p>
 */
public enum IdentityVerificationResult {
    /** 比对通过。 */
    PASSED,
    /** 比对未通过。 */
    REJECTED
}
