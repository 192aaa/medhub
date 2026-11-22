package com.sdas.server.pharmacy.service;

import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.pharmacy.model.DispenseStatus;
import java.time.LocalDateTime;

/**
 * 取药状态派生规则（纯函数，无 Spring 依赖）。
 *
 * <p>唯一来源：specs/004-prescription-dispensing/data-model.md 1.2 与 §4 组合矩阵。
 * 判定优先级：{@code REVOKED} → 有核销记录 {@code DISPENSED} → 过期 {@code EXPIRED} → {@code PENDING}。
 * 边界：{@code now == valid_until} 视为未过期（闭区间）；{@code valid_until} 为 null 按过期处理。</p>
 */
public final class DispenseStatusResolver {

    private DispenseStatusResolver() {
    }

    /**
     * 派生取药状态。
     *
     * @param status     处方状态（003，不新增取值）
     * @param dispensed  是否存在核销记录
     * @param validUntil 有效期截止，可为 null
     * @param now        服务端当前时间
     * @return 派生状态
     */
    public static DispenseStatus resolve(PrescriptionStatus status, boolean dispensed,
                                         LocalDateTime validUntil, LocalDateTime now) {
        if (status == PrescriptionStatus.REVOKED) {
            return DispenseStatus.REVOKED;
        }
        if (dispensed) {
            return DispenseStatus.DISPENSED;
        }
        if (isExpired(validUntil, now)) {
            return DispenseStatus.EXPIRED;
        }
        return DispenseStatus.PENDING;
    }

    /**
     * 是否可核销：仅 {@code PENDING} 可核销。
     *
     * @param status 派生状态
     * @return 可核销为 true
     */
    public static boolean dispensable(DispenseStatus status) {
        return status == DispenseStatus.PENDING;
    }

    /**
     * 是否已过有效期（响应字段 {@code expired} 口径）。
     *
     * @param validUntil 有效期截止，可为 null
     * @param now        服务端当前时间
     * @return 过期或有效期缺失为 true
     */
    public static boolean isExpired(LocalDateTime validUntil, LocalDateTime now) {
        return validUntil == null || now.isAfter(validUntil);
    }
}
