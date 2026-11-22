package com.sdas.server.appointment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付状态查询响应（契约端点 6b，未支付字段可为空）。 */
public record PaymentStatusResponse(Long appointmentId, Long paymentId, BigDecimal amount,
                                   String channel, String status, LocalDateTime paidAt,
                                   String paymentStatus, String appointmentStatus) {
}
