package com.sdas.server.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 提交图文问诊单请求（契约端点 8）。 */
public record ConsultationSubmitRequest(@NotNull Long appointmentId,
                                       @NotBlank @Size(min = 10, max = 200) String complaint,
                                       @Size(max = 2000) String description,
                                       List<String> imageUrls) {
}
