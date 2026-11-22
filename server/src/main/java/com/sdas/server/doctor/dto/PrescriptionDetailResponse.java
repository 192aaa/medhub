package com.sdas.server.doctor.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 端点 8 响应 data（处方详情）。
 *
 * @param prescriptionId 处方主键
 * @param consultationId 关联问诊单
 * @param status         处方状态
 * @param items          药品明细
 * @param signedAt       签名时间
 * @param validUntil     有效期截止
 * @param expired        是否已过期
 * @param qrCode         二维码内容
 * @param watermark      水印内容
 */
public record PrescriptionDetailResponse(
        long prescriptionId,
        long consultationId,
        String status,
        List<PrescriptionItemResponse> items,
        LocalDateTime signedAt,
        LocalDateTime validUntil,
        boolean expired,
        String qrCode,
        String watermark) {
}
