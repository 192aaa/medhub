package com.sdas.server.doctor.dto;

import com.sdas.server.appointment.dto.ConsultationImageDto;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 端点 3 响应 data（问诊单详情）。
 *
 * @param consultationId 问诊单主键
 * @param patientName    脱敏患者姓名
 * @param complaint      主诉
 * @param description    选填描述
 * @param images         图片列表
 * @param summary        AI 病情摘要
 * @param status         问诊单状态
 * @param acceptedAt     接诊时间（未接诊为 null）
 * @param createdAt      提交时间
 */
public record ConsultationDetailResponse(
        long consultationId,
        String patientName,
        String complaint,
        String description,
        List<ConsultationImageDto> images,
        String summary,
        String status,
        LocalDateTime acceptedAt,
        LocalDateTime createdAt) {
}
