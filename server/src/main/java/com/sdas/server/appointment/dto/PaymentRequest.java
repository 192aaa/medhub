package com.sdas.server.appointment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 挂号费支付请求（契约端点 6a，MVP 仅 MOCK）。 */
public record PaymentRequest(@NotNull String channel,
                            @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
}
