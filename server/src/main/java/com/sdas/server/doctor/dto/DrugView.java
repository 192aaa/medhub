package com.sdas.server.doctor.dto;

/**
 * 端点 5 药品条目。
 *
 * @param drugId           主键
 * @param drugName         药品名称
 * @param spec             规格
 * @param unit             单位
 * @param prescriptionOnly 是否处方药
 */
public record DrugView(
        long drugId,
        String drugName,
        String spec,
        String unit,
        boolean prescriptionOnly) {
}
