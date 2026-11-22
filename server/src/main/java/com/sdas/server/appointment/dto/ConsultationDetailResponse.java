package com.sdas.server.appointment.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 问诊单详情响应（契约端点 9）。 */
public record ConsultationDetailResponse(Long consultationId, Long appointmentId, Long doctorId,
                                        Long departmentId, String complaint, String description,
                                        List<ConsultationImageDto> images, String summary,
                                        String status, LocalDateTime createdAt,
                                        LocalDateTime updatedAt) {
}
