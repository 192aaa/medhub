package com.sdas.server.doctor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 端点 7 请求体（开具处方）。
 *
 * @param items 药品明细（1-10 项）
 */
public record PrescriptionCreateRequest(
        @NotEmpty @Size(max = 10) List<@Valid PrescriptionItemRequest> items) {
}
