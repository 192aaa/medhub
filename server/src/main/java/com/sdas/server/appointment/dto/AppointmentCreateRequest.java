package com.sdas.server.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建挂号预约请求（契约端点 5）。 */
public record AppointmentCreateRequest(@NotNull Long doctorId, @NotNull Long scheduleId,
                                      @Size(max = 2000) String patientNote) {
}
