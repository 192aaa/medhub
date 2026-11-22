package com.sdas.server.pharmacy.entity;

import com.sdas.server.pharmacy.model.PharmacyAccountStatus;
import java.time.LocalDateTime;

/**
 * 药店账号实体。
 *
 * <p>表结构来源：specs/004-prescription-dispensing/data-model.md 2.1。
 * {@code status} 由
 * {@link com.sdas.server.pharmacy.model.converter.PharmacyAccountStatusConverter} 显式转换，
 * 不使用 {@code @Enumerated}；{@code nameMask} 只存脱敏名（如「王*」）。</p>
 *
 * @param id           主键
 * @param username     登录名
 * @param passwordHash BCrypt 口令哈希
 * @param nameMask     经办人脱敏名
 * @param pharmacyName 药店名称
 * @param status       账号状态
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @param version      乐观锁
 */
public record PharmacyAccount(
        Long id,
        String username,
        String passwordHash,
        String nameMask,
        String pharmacyName,
        PharmacyAccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
