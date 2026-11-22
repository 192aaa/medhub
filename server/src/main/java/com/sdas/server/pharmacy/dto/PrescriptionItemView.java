package com.sdas.server.pharmacy.dto;

/**
 * 处方明细视图（验真与核销响应共用）。
 *
 * @param drugId   药品主键
 * @param drugName 药品名
 * @param spec     规格
 * @param dosage   用法用量
 * @param caution  注意事项
 */
public record PrescriptionItemView(
        long drugId,
        String drugName,
        String spec,
        String dosage,
        String caution) {
}
