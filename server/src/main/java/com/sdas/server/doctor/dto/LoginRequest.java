package com.sdas.server.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 端点 1 请求体（contracts/doctor-consultation-api.md 端点 1）。
 *
 * @param username 登录名
 * @param password 密码
 */
public record LoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 100) String password) {
}
