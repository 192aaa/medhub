package com.sdas.server.pharmacy.dto;

/**
 * 药店登录响应（端点 1，契约 004）。不签发 Refresh Token。
 *
 * @param accessToken  访问令牌（role=PHARMACY）
 * @param pharmacyId   药店账号主键
 * @param nameMask     经办人脱敏名
 * @param pharmacyName 药店名称
 * @param expiresIn    有效期（秒）
 */
public record PharmacyLoginResponse(
        String accessToken,
        long pharmacyId,
        String nameMask,
        String pharmacyName,
        long expiresIn) {
}
