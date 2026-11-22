package com.sdas.server.identity.dto;

import com.sdas.server.identity.model.AuthStatus;

/**
 * 端点 7 成功响应 data（contracts/patient-identity-api.md 端点 7）。
 *
 * <p>禁止返回 IdentityVerificationResult 枚举与白名单命中信息（FR-019、SC-005）。</p>
 *
 * @param authStatus          认证状态，成功恒为 VERIFIED
 * @param consultationAllowed 是否允许问诊，与 authStatus 严格联动
 * @param verifiedAt          认证通过时间，ISO 8601
 * @param maskedName          脱敏姓名
 * @param maskedIdCard        脱敏证件号
 */
public record IdentityVerificationResponse(
        AuthStatus authStatus,
        boolean consultationAllowed,
        String verifiedAt,
        String maskedName,
        String maskedIdCard) {
}
