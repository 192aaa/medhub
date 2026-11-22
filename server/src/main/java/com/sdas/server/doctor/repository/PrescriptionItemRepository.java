package com.sdas.server.doctor.repository;

import com.sdas.server.doctor.entity.PrescriptionItem;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 处方药品明细持久化（JdbcTemplate）。表结构来源：data-model.md 2.6。 */
@Repository
public class PrescriptionItemRepository {

    private static final RowMapper<PrescriptionItem> MAPPER = (rs, rowNum) -> new PrescriptionItem(
            rs.getLong("id"), rs.getLong("prescription_id"), rs.getLong("drug_id"),
            rs.getString("spec"), rs.getString("dosage"), rs.getString("caution"),
            toLdt(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造处方明细仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PrescriptionItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param item 明细 @return 新主键 */
    public long insert(PrescriptionItem item) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO prescription_item (prescription_id, drug_id, spec, dosage, caution) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, item.prescriptionId());
            ps.setLong(2, item.drugId());
            ps.setString(3, item.spec());
            ps.setString(4, item.dosage());
            ps.setString(5, item.caution());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain prescription_item id");
        }
        return key.longValue();
    }

    /** @param prescriptionId 处方主键 @return 明细列表 */
    public List<PrescriptionItem> findByPrescriptionId(long prescriptionId) {
        return jdbcTemplate.query(
                "SELECT * FROM prescription_item WHERE prescription_id = ? ORDER BY id ASC",
                MAPPER, prescriptionId);
    }

    /**
     * 按处方批量查询明细（AI健康咨询病历上下文，只读）。
     *
     * @param prescriptionIds 处方主键列表
     * @return 明细列表（按主键正序）
     */
    public List<PrescriptionItem> findByPrescriptionIdIn(List<Long> prescriptionIds) {
        if (prescriptionIds == null || prescriptionIds.isEmpty()) {
            return List.of();
        }
        String placeholders =
                String.join(",", java.util.Collections.nCopies(prescriptionIds.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM prescription_item WHERE prescription_id IN ("
                        + placeholders + ") ORDER BY id ASC",
                MAPPER, prescriptionIds.toArray());
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
