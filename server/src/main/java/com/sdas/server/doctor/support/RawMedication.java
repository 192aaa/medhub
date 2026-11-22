package com.sdas.server.doctor.support;

/**
 * 模型输出的原始用药条目（只含通用名，不含 drugId）。
 *
 * @param drugName 药品通用名
 * @param dosage   用法用量（可能缺失）
 * @param caution  注意事项（可能缺失）
 */
public record RawMedication(String drugName, String dosage, String caution) {
}
