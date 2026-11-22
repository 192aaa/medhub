package com.sdas.server.doctor.dto;

import java.time.LocalDateTime;

/**
 * 端点 2 响应元素（待接诊/已接诊列表）。
 *
 * @param consultationId 问诊单主键
 * @param patientName    脱敏患者姓名
 * @param complaint      主诉
 * @param status         问诊单状态
 * @param submittedAt    提交时间
 */
public record ConsultationListItem(
        long consultationId,
        String patientName,
        String complaint,
        String status,
        LocalDateTime submittedAt) {
}
