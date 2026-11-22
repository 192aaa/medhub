package com.sdas.server.identity.dto;

import com.sdas.server.identity.model.VerificationPurpose;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 端点 1 请求体（contracts/patient-identity-api.md 端点 1）。
 *
 * @param phone   11 位中国大陆手机号
 * @param purpose 验证码用途
 */
public record VerificationCodeSendRequest(
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "phone") String phone,
        @NotNull(message = "purpose") VerificationPurpose purpose) {
}
