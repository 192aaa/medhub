package com.sdas.server.identity.dto;

import com.sdas.server.identity.model.AuthStatus;

/**
 * 端点 3 响应 data；禁止包含 refreshToken 明文字段。
 *
 * @param patientId                  患者主键
 * @param maskedPhone                脱敏手机号
 * @param authStatus                 认证状态
 * @param consultationAllowed        是否允许问诊
 * @param accessToken                JWT
 * @param accessTokenExpiresInSeconds 有效期 7200
 */
public record PatientLoginResponse(
        long patientId,
        String maskedPhone,
        AuthStatus authStatus,
        boolean consultationAllowed,
        String accessToken,
        long accessTokenExpiresInSeconds) {
}
