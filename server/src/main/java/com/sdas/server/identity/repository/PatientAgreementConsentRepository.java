package com.sdas.server.identity.repository;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.entity.PatientAgreementConsent;
import com.sdas.server.identity.model.converter.AgreementConsentStatusConverter;
import java.sql.Statement;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 协议同意记录持久化。
 *
 * <p>表结构来源：specs/001-patient-identity/data-model.md 3.3。</p>
 */
@Repository
public class PatientAgreementConsentRepository {

    private static final AgreementConsentStatusConverter CONVERTER =
            new AgreementConsentStatusConverter();

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造协议同意仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PatientAgreementConsentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入同意记录；uk_patient_version 冲突映射为业务异常。
     *
     * @param consent 待插入记录
     * @return 新记录主键
     */
    public long insert(PatientAgreementConsent consent) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO patient_agreement_consent (patient_id, agreement_version, "
                                + "consent_status, agreed_at, created_at, updated_at, version) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, consent.patientId());
                statement.setString(2, consent.agreementVersion());
                statement.setString(3, CONVERTER.convertToDatabaseColumn(consent.consentStatus()));
                statement.setTimestamp(4, java.sql.Timestamp.valueOf(consent.agreedAt()));
                statement.setTimestamp(5, java.sql.Timestamp.valueOf(consent.createdAt()));
                statement.setTimestamp(6, java.sql.Timestamp.valueOf(consent.updatedAt()));
                statement.setInt(7, consent.version());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain agreement consent id");
        }
        return key.longValue();
    }
}
