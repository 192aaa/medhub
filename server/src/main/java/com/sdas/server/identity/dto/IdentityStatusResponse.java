package com.sdas.server.identity.dto;

import com.sdas.server.identity.model.AuthStatus;

/**
 * 端点 8 响应 data（contracts/patient-identity-api.md 端点 8）。
 *
 * <p>未认证时 verifiedAt / maskedName / maskedIdCard 三个字段仍须存在且为 null。</p>
 *
 * @param authStatus          认证状态
 * @param consultationAllowed authStatus == VERIFIED 时为 true
 * @param verifiedAt          认证通过时间，未认证为 null
 * @param maskedName          脱敏姓名，未认证为 null
 * @param maskedIdCard        脱敏证件号，未认证为 null
 */
public record IdentityStatusResponse(
        AuthStatus authStatus,
        boolean consultationAllowed,
        String verifiedAt,
        String maskedName,
        String maskedIdCard) {
}
