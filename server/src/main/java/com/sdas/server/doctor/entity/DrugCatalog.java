package com.sdas.server.doctor.entity;

import java.time.LocalDateTime;

/**
 * 药品目录实体（只读，Flyway 预置）。
 *
 * <p>表结构来源：specs/003-doctor-consultation/data-model.md 2.4。</p>
 *
 * @param id               主键
 * @param name             药品名称
 * @param spec             规格
 * @param manufacturer     厂商
 * @param dosageForm       剂型
 * @param unit             单位
 * @param prescriptionOnly 是否处方药
 * @param enabled          是否启用
 * @param source           来源（仅 PRESET）
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 * @param version          乐观锁
 */
public record DrugCatalog(
        Long id,
        String name,
        String spec,
        String manufacturer,
        String dosageForm,
        String unit,
        boolean prescriptionOnly,
        boolean enabled,
        String source,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
