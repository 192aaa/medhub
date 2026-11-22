package com.sdas.server.pharmacy.repository;

import com.sdas.server.pharmacy.entity.DispenseRecord;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 处方核销记录持久化（JdbcTemplate）。表结构来源：data-model.md 2.2。
 *
 * <p>{@code insert} 命中 {@code uk_dispense_prescription} 时抛出
 * {@code DuplicateKeyException}，由核销服务转换为幂等分支（research.md R-08）。</p>
 */
@Repository
public class DispenseRecordRepository {

    private static final RowMapper<DispenseRecord> MAPPER = (rs, rowNum) -> new DispenseRecord(
            rs.getLong("id"), rs.getLong("prescription_id"), rs.getLong("pharmacy_id"),
            toLdt(rs.getTimestamp("dispensed_at")), rs.getString("qr_token_version"),
            rs.getString("proof_digest"), toLdt(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造核销记录仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DispenseRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param record 核销记录 @return 新主键 */
    public long insert(DispenseRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO prescription_dispense_record (prescription_id, pharmacy_id, "
                            + "dispensed_at, qr_token_version, proof_digest) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, record.prescriptionId());
            ps.setLong(2, record.pharmacyId());
            ps.setTimestamp(3, Timestamp.valueOf(record.dispensedAt()));
            ps.setString(4, record.qrTokenVersion());
            ps.setString(5, record.proofDigest());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain dispense record id");
        }
        return key.longValue();
    }

    /** @param prescriptionId 处方主键 @return 核销记录（若已取药） */
    public Optional<DispenseRecord> findByPrescriptionId(long prescriptionId) {
        return jdbcTemplate.query(
                "SELECT * FROM prescription_dispense_record WHERE prescription_id = ?",
                MAPPER, prescriptionId).stream().findFirst();
    }

    /**
     * 某药店最近的核销记录（仅供测试与审计排查，不暴露为端点）。
     *
     * @param pharmacyId 药店主键
     * @param limit      条数上限
     * @return 核销记录列表（时间倒序）
     */
    public List<DispenseRecord> findTopByPharmacyId(long pharmacyId, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM prescription_dispense_record WHERE pharmacy_id = ? "
                        + "ORDER BY dispensed_at DESC, id DESC LIMIT ?",
                MAPPER, pharmacyId, limit);
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
