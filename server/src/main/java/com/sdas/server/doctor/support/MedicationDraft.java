package com.sdas.server.doctor.support;

/**
 * 映射后的用药草稿（drugId 与规格来自药品目录）。
 *
 * @param drugId          目录主键
 * @param drugName        目录药品名
 * @param spec            目录规格
 * @param dosage          用法用量
 * @param caution         注意事项
 * @param needsCompletion 医生是否需要补齐
 */
public record MedicationDraft(
        Long drugId,
        String drugName,
        String spec,
        String dosage,
        String caution,
        boolean needsCompletion) {
}
