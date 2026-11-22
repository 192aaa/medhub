package com.sdas.server.doctor.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 端点 7 响应 data（开具处方）。
 *
 * @param prescriptionId 处方主键
 * @param consultationId 关联问诊单
 * @param status         处方状态（SIGNED）
 * @param items          药品明细
 * @param signedAt       签名时间
 * @param validUntil     有效期截止
 * @param qrCode         二维码内容（验真链接）
 * @param watermark      水印内容
 */
public record PrescriptionResponse(
        long prescriptionId,
        long consultationId,
        String status,
        List<PrescriptionItemResponse> items,
        LocalDateTime signedAt,
        LocalDateTime validUntil,
        String qrCode,
        String watermark) {
}
