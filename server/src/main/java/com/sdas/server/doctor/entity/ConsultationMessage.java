package com.sdas.server.doctor.entity;

import com.sdas.server.doctor.model.MessageType;
import java.time.LocalDateTime;

/**
 * 接诊对话消息实体。
 *
 * <p>表结构来源：specs/003-doctor-consultation/data-model.md 2.3。</p>
 *
 * @param id             主键
 * @param consultationId 关联问诊单
 * @param senderType     发送方（DOCTOR/PATIENT/SYSTEM）
 * @param senderId       发送方主键（医生或患者）
 * @param content        内容（医生回复 ≤ 1000 字）
 * @param createdAt      发送时间
 */
public record ConsultationMessage(
        Long id,
        Long consultationId,
        MessageType senderType,
        Long senderId,
        String content,
        LocalDateTime createdAt) {
}
