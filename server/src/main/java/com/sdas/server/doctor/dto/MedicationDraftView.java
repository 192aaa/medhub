package com.sdas.server.doctor.dto;

/**
 * 用药草稿（端点 1/2/3 响应 medicationDrafts[]）。
 *
 * <p>{@code drugId} 由服务端按药品目录映射产生，模型输出中不含该字段；
 * {@code needsCompletion=true} 表示用法用量缺失或异常，医生必须补齐。</p>
 *
 * @param drugId          目录主键
 * @param drugName        药品通用名（目录值）
 * @param spec            规格（目录值）
 * @param dosage          用法用量
 * @param caution         注意事项
 * @param needsCompletion 是否需要医生补齐
 */
public record MedicationDraftView(
        Long drugId,
        String drugName,
        String spec,
        String dosage,
        String caution,
        boolean needsCompletion) {
}
