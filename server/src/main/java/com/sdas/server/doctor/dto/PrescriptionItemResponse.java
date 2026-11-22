package com.sdas.server.doctor.dto;

/**
 * 处方药品明细响应元素。
 *
 * @param drugId   药品主键
 * @param drugName 药品名称
 * @param spec     规格
 * @param dosage   用法用量
 * @param caution  注意事项
 */
public record PrescriptionItemResponse(long drugId, String drugName, String spec, String dosage,
                                       String caution) {
}
