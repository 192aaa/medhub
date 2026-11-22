package com.sdas.server.identity.entity;

import java.time.LocalDateTime;

/**
 * 医院白名单实体（本功能只读）。
 *
 * <p>字段唯一来源：specs/001-patient-identity/data-model.md 3.5。</p>
 *
 * @param id            主键
 * @param realName      姓名（精确比对）
 * @param idCardHash    证件号 HMAC 摘要
 * @param idCardCipher  证件号密文
 * @param enabled       是否参与比对
 * @param source        数据来源标记
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 * @param version       乐观锁版本
 */
public record HospitalIdentityWhitelist(
        Long id,
        String realName,
        String idCardHash,
        byte[] idCardCipher,
        boolean enabled,
        String source,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
