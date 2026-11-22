package com.sdas.server.pharmacy.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方验真响应（端点 2，契约 004）。
 *
 * <p>{@code verificationResult} 固定 {@code VALID}：失败一律走异常分支，响应体不含任何处方字段。
 * {@code dispenseStatus} 为派生值（不落库），{@code dispensable} 等价于
 * {@code dispenseStatus == PENDING}。</p>
 *
 * @param verificationResult 固定 VALID
 * @param prescriptionId     处方主键
 * @param doctorNameMask     开方医生脱敏名
 * @param signedAt           签名时间
 * @param validUntil         有效期截止
 * @param expired            服务端时间是否已过期
 * @param status             003 处方状态
 * @param dispenseStatus     派生取药状态
 * @param dispensable        是否可取药
 * @param dispensedAt        已取药时间（可为 null）
 * @param dispensedByMask    核销人脱敏名（可为 null）
 * @param dispenseRecordId   核销记录主键（可为 null）
 * @param watermark          处方水印
 * @param verifyCode         验真码回显
 * @param items              药品明细
 */
public record VerificationResponse(
        String verificationResult,
        long prescriptionId,
        String doctorNameMask,
        LocalDateTime signedAt,
        LocalDateTime validUntil,
        boolean expired,
        String status,
        String dispenseStatus,
        boolean dispensable,
        LocalDateTime dispensedAt,
        String dispensedByMask,
        Long dispenseRecordId,
        String watermark,
        String verifyCode,
        List<PrescriptionItemView> items) {
}
