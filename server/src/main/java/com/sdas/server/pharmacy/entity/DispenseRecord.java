package com.sdas.server.pharmacy.entity;

import java.time.LocalDateTime;

/**
 * 处方核销记录实体。
 *
 * <p>表结构来源：specs/004-prescription-dispensing/data-model.md 2.2。
 * {@code uk_dispense_prescription} 保证一处方最多一条记录，是并发幂等的最终保证；
 * {@code proofDigest} 存凭证的完整 SHA-256（64 位十六进制），不存令牌明文。</p>
 *
 * @param id              主键
 * @param prescriptionId  处方主键
 * @param pharmacyId      核销药店
 * @param dispensedAt     核销时间（服务端时间）
 * @param qrTokenVersion  核销所用令牌版本
 * @param proofDigest     凭证 SHA-256（完整 64 位十六进制）
 * @param createdAt       创建时间
 */
public record DispenseRecord(
        Long id,
        Long prescriptionId,
        Long pharmacyId,
        LocalDateTime dispensedAt,
        String qrTokenVersion,
        String proofDigest,
        LocalDateTime createdAt) {
}
