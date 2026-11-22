package com.sdas.server.doctor.entity;

import java.time.LocalDateTime;

/**
 * 处方药品明细实体。
 *
 * <p>表结构来源：specs/003-doctor-consultation/data-model.md 2.6。</p>
 *
 * @param id             主键
 * @param prescriptionId 关联处方
 * @param drugId         关联药品目录
 * @param spec           规格快照
 * @param dosage         用法用量
 * @param caution        注意事项
 * @param createdAt      创建时间
 */
public record PrescriptionItem(
        Long id,
        Long prescriptionId,
        Long drugId,
        String spec,
        String dosage,
        String caution,
        LocalDateTime createdAt) {
}
