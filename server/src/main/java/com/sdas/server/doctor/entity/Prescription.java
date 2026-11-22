package com.sdas.server.doctor.entity;

import com.sdas.server.doctor.model.PrescriptionStatus;
import java.time.LocalDateTime;

/**
 * 电子处方实体。
 *
 * <p>表结构来源：specs/003-doctor-consultation/data-model.md 2.5。
 * UNIQUE(consultation_id) 保证一张问诊单仅一张处方。</p>
 *
 * @param id             主键
 * @param consultationId 关联问诊单
 * @param doctorId       开具医生
 * @param patientId      患者
 * @param status         处方状态
 * @param signature      签名摘要
 * @param qrCode         二维码内容（验真链接）
 * @param watermark      水印内容
 * @param signedAt       签名时间
 * @param validUntil     有效期截止
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 * @param version        乐观锁
 */
public record Prescription(
        Long id,
        Long consultationId,
        Long doctorId,
        Long patientId,
        PrescriptionStatus status,
        String signature,
        String qrCode,
        String watermark,
        LocalDateTime signedAt,
        LocalDateTime validUntil,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
