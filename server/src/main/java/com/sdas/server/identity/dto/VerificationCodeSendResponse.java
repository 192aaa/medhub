package com.sdas.server.identity.dto;

import com.sdas.server.identity.model.VerificationPurpose;

/**
 * 端点 1 响应 data；禁止包含验证码明文字段。
 *
 * @param purpose                  回显用途
 * @param maskedPhone              脱敏手机号
 * @param expiresInSeconds         有效期（300）
 * @param nextSendAllowedInSeconds 下次可发送间隔（60）
 */
public record VerificationCodeSendResponse(
        VerificationPurpose purpose,
        String maskedPhone,
        long expiresInSeconds,
        long nextSendAllowedInSeconds) {
}
