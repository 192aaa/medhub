package com.sdas.server.pharmacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 药店登录请求（端点 1，契约 004）。
 *
 * @param username 登录名
 * @param password 明文口令（仅经 TLS 传输，服务端只比对 BCrypt 哈希）
 */
public record PharmacyLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 100) String password) {
}
