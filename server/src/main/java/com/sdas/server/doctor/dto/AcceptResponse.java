package com.sdas.server.doctor.dto;

import java.time.LocalDateTime;

/**
 * 端点 4 响应 data（接诊）。
 *
 * @param consultationId 问诊单主键
 * @param status         问诊单状态（IN_PROGRESS）
 * @param acceptedAt     接诊时间
 * @param doctorId       接诊医生
 */
public record AcceptResponse(long consultationId, String status, LocalDateTime acceptedAt,
                             long doctorId) {
}
