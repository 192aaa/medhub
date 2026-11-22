package com.sdas.server.identity.dto;

import com.sdas.server.identity.model.AuthStatus;

/**
 * 端点 2 响应 data；新注册固定 UNVERIFIED / consultationAllowed=false。
 *
 * @param patientId                  患者主键
 * @param maskedPhone                脱敏手机号
 * @param authStatus                 认证状态
 * @param consultationAllowed        是否允许问诊
 * @param accessToken                JWT
 * @param accessTokenExpiresInSeconds 有效期 7200
 * @param registeredAt               注册时间（ISO 8601）
 * @param agreementVersion           已记录的协议版本
 */
public record PatientRegisterResponse(
        long patientId,
        String maskedPhone,
        AuthStatus authStatus,
        boolean consultationAllowed,
        String accessToken,
        long accessTokenExpiresInSeconds,
        String registeredAt,
        String agreementVersion) {
}
