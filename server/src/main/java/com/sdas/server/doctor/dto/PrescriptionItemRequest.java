package com.sdas.server.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 端点 7 请求体 items 元素（药品明细）。
 *
 * @param drugId  药品主键（必须存在于目录且启用）
 * @param spec    规格（默认取目录值）
 * @param dosage  用法用量
 * @param caution 注意事项
 */
public record PrescriptionItemRequest(
        @NotNull Long drugId,
        @Size(max = 128) String spec,
        @NotBlank @Size(max = 255) String dosage,
        @Size(max = 500) String caution) {
}
