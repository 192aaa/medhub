package com.sdas.server.identity.repository;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.entity.PatientIdentity;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 实名信息持久化；唯一约束冲突统一映射为 AUTHENTICATION_FAILED（FR-019、SC-005）。
 *
 * <p>表结构来源：specs/001-patient-identity/data-model.md 3.2。</p>
 */
@Repository
public class PatientIdentityRepository {

    private static final RowMapper<PatientIdentity> MAPPER = (rs, rowNum) -> new PatientIdentity(
            rs.getLong("id"), rs.getLong("patient_id"), rs.getBytes("real_name_cipher"),
            rs.getString("real_name_mask"), rs.getString("id_card_hash"),
            rs.getBytes("id_card_cipher"), rs.getString("id_card_mask"),
            rs.getTimestamp("verified_at").toLocalDateTime(),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(), rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造实名信息仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PatientIdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按患者主键查询实名信息。
     *
     * @param patientId 患者主键
     * @return 实名信息，不存在时为空
     */
    public Optional<PatientIdentity> findByPatientId(long patientId) {
        return jdbcTemplate.query(
                "SELECT * FROM patient_identity WHERE patient_id = ?", MAPPER, patientId)
                .stream().findFirst();
    }

    /**
     * 插入实名信息；uk_id_card_hash / uk_patient_id 冲突映射为统一失败。
     *
     * @param identity 待插入实名信息
     * @return 新记录主键
     */
    public long insert(PatientIdentity identity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO patient_identity (patient_id, real_name_cipher, "
                                + "real_name_mask, id_card_hash, id_card_cipher, id_card_mask, "
                                + "verified_at, created_at, updated_at, version) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, identity.patientId());
                statement.setBytes(2, identity.realNameCipher());
                statement.setString(3, identity.realNameMask());
                statement.setString(4, identity.idCardHash());
                statement.setBytes(5, identity.idCardCipher());
                statement.setString(6, identity.idCardMask());
                statement.setTimestamp(7, java.sql.Timestamp.valueOf(identity.verifiedAt()));
                statement.setTimestamp(8, java.sql.Timestamp.valueOf(identity.createdAt()));
                statement.setTimestamp(9, java.sql.Timestamp.valueOf(identity.updatedAt()));
                statement.setInt(10, identity.version());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain patient identity id");
        }
        return key.longValue();
    }
}
