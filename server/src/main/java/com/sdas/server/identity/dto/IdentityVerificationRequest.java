package com.sdas.server.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 端点 7 请求体（contracts/patient-identity-api.md 端点 7）。
 *
 * <p>字段唯一来源：契约请求体；禁止出现 idCardHash / idCardCipher /
 * realNameCipher 等内部字段。姓名不做 trim 后静默修正。</p>
 *
 * @param realName     姓名，1-50 个字符且不含控制字符
 * @param idCardNumber 18 位证件号，校验位由服务按 GB 11643 校验
 */
public record IdentityVerificationRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String realName,
        @NotBlank @Pattern(regexp = "^\\d{17}[0-9Xx]$") String idCardNumber) {
}
