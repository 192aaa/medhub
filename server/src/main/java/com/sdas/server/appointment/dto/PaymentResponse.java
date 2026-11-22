package com.sdas.server.appointment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 挂号费支付响应（契约端点 6a）。 */
public record PaymentResponse(Long appointmentId, Long paymentId, BigDecimal amount, String channel,
                             String status, LocalDateTime paidAt, String paymentStatus,
                             String appointmentStatus) {
}
