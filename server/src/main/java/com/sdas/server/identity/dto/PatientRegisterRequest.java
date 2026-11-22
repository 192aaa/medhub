package com.sdas.server.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 端点 2 请求体（contracts/patient-identity-api.md 端点 2）。
 *
 * <p>Bean Validation 只做首轮格式校验；密码「至少一个字母 + 一个数字」与协议版本
 * 一致性由 PatientAccountService 二次校验。</p>
 *
 * @param phone             登录手机号
 * @param verificationCode  6 位数字验证码
 * @param password          6-20 位字母数字组合
 * @param agreementAccepted 是否勾选协议
 * @param agreementVersion  协议版本
 */
public record PatientRegisterRequest(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String verificationCode,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6,20}$") String password,
        @NotNull Boolean agreementAccepted,
        @NotBlank String agreementVersion) {
}
