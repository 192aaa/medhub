package com.sdas.server.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AI 智能导诊请求（契约端点 4）。 */
public record TriageRequest(@NotBlank @Size(max = 500) String complaint) {
}
