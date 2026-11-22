package com.sdas.server.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 端点 3 请求体（contracts/patient-identity-api.md 端点 3）。
 *
 * @param phone    登录手机号
 * @param password 6-20 位字母数字组合
 */
public record PatientLoginRequest(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6,20}$") String password) {
}
