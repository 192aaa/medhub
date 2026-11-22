package com.sdas.server.identity.entity;

import java.time.LocalDateTime;

/**
 * 实名信息实体（一账户一条）。
 *
 * <p>字段唯一来源：specs/001-patient-identity/data-model.md 3.2。</p>
 *
 * @param id              主键
 * @param patientId       患者主键
 * @param realNameCipher  姓名密文
 * @param realNameMask    脱敏姓名
 * @param idCardHash      证件号 HMAC 摘要
 * @param idCardCipher    证件号密文
 * @param idCardMask      脱敏证件号
 * @param verifiedAt      比对通过时间
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 * @param version         乐观锁版本
 */
public record PatientIdentity(
        Long id,
        Long patientId,
        byte[] realNameCipher,
        String realNameMask,
        String idCardHash,
        byte[] idCardCipher,
        String idCardMask,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
