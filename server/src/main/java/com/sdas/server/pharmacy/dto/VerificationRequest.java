package com.sdas.server.pharmacy.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 处方验真请求（端点 2，契约 004）。
 *
 * <p>{@code qrContent} 与「{@code prescriptionId} + {@code verifyCode}」二选一，互斥；
 * 互斥与必填组合由服务层判定（统一 {@code BAD_REQUEST}），本 DTO 只做单项格式约束。</p>
 *
 * @param qrContent      扫码原始内容（完整 URL 或裸令牌）
 * @param prescriptionId 手工输入处方号
 * @param verifyCode     手工输入验真码（12 位）
 */
public record VerificationRequest(
        @Size(min = 8, max = 1024) String qrContent,
        @Positive Long prescriptionId,
        @Pattern(regexp = "^[A-Za-z0-9_-]{12}$") String verifyCode) {
}
