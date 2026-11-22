package com.sdas.server.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 端点 6 请求体（contracts/patient-identity-api.md 端点 6）。
 *
 * <p>Bean Validation 只做首轮格式校验；密码「至少一个字母 + 一个数字」由
 * PasswordResetService 二次校验，与注册保持一致。</p>
 *
 * @param phone            登录手机号
 * @param verificationCode 6 位数字验证码（用途须为 RESET_PASSWORD）
 * @param newPassword      6-20 位字母数字组合
 */
public record PasswordResetRequest(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String verificationCode,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6,20}$") String newPassword) {
}
