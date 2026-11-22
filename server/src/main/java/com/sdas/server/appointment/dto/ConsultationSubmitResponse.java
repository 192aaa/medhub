package com.sdas.server.appointment.dto;

import java.time.LocalDateTime;

/** 提交图文问诊单响应（契约端点 8）。 */
public record ConsultationSubmitResponse(Long consultationId, Long appointmentId, Long doctorId,
                                        Long departmentId, String complaint, int imageCount,
                                        String status, String summary, LocalDateTime createdAt) {
}
