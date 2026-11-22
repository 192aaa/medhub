package com.sdas.server.appointment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 创建挂号预约响应（契约端点 5）。 */
public record AppointmentCreateResponse(Long appointmentId, Long doctorId, Long departmentId,
                                       LocalDate appointmentDate, String period,
                                       BigDecimal feeAmount, String paymentStatus,
                                       String appointmentStatus, LocalDateTime createdAt) {
}
