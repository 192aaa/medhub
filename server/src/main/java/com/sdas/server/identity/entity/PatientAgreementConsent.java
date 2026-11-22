package com.sdas.server.identity.entity;

import com.sdas.server.identity.model.AgreementConsentStatus;
import java.time.LocalDateTime;

/**
 * 协议同意记录实体。
 *
 * <p>字段唯一来源：specs/001-patient-identity/data-model.md 3.3。</p>
 *
 * @param id                主键
 * @param patientId         患者主键
 * @param agreementVersion  协议版本
 * @param consentStatus     同意状态
 * @param agreedAt          同意时间
 * @param createdAt         创建时间
 * @param updatedAt         更新时间
 * @param version           乐观锁版本
 */
public record PatientAgreementConsent(
        Long id,
        Long patientId,
        String agreementVersion,
        AgreementConsentStatus consentStatus,
        LocalDateTime agreedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
