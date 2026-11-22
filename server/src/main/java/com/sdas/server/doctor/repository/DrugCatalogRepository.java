package com.sdas.server.doctor.repository;

import com.sdas.server.doctor.entity.DrugCatalog;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 药品目录持久化（只读）。表结构来源：data-model.md 2.4。 */
@Repository
public class DrugCatalogRepository {

    private static final RowMapper<DrugCatalog> MAPPER = (rs, rowNum) -> new DrugCatalog(
            rs.getLong("id"), rs.getString("name"), rs.getString("spec"),
            rs.getString("manufacturer"), rs.getString("dosage_form"), rs.getString("unit"),
            rs.getBoolean("prescription_only"), rs.getBoolean("enabled"), rs.getString("source"),
            toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")),
            rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造药品目录仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DrugCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param id 主键 @return 启用状态的药品 */
    public Optional<DrugCatalog> findByIdAndEnabled(long id) {
        return jdbcTemplate.query(
                "SELECT * FROM drug_catalog WHERE id = ? AND enabled = 1", MAPPER, id)
                .stream().findFirst();
    }

    /** @param id 主键 @return 药品（不限启用状态，供处方明细回显） */
    public Optional<DrugCatalog> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM drug_catalog WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /**
     * 按主键批量查询药品（AI健康咨询病历上下文，只读）。
     *
     * @param ids 主键列表
     * @return 药品列表（调用方按 enabled 过滤）
     */
    public List<DrugCatalog> findByIdsIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM drug_catalog WHERE id IN (" + placeholders + ")",
                MAPPER, ids.toArray());
    }

    /**
     * 按药品名称批量查询启用药品（006 草稿映射，只读）。
     *
     * @param names 药品名称列表（模型输出的通用名）
     * @return 命中的启用药品
     */
    public List<DrugCatalog> findEnabledByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",",
                java.util.Collections.nCopies(names.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM drug_catalog WHERE enabled = 1 AND name IN ("
                + placeholders + ") ORDER BY id", MAPPER, names.toArray());
    }

    /**
     * 启用药品分页查询（端点 5）。
     *
     * @param keyword 名称关键字（可空，≤50 字符由 Controller 校验）
     * @param offset  偏移量
     * @param limit   条数
     * @return 药品列表（按 id 升序）
     */
    public List<DrugCatalog> findEnabledPage(String keyword, int offset, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return jdbcTemplate.query(
                    "SELECT * FROM drug_catalog WHERE enabled = 1 ORDER BY id LIMIT ? OFFSET ?",
                    MAPPER, limit, offset);
        }
        return jdbcTemplate.query(
                "SELECT * FROM drug_catalog WHERE enabled = 1 AND name LIKE ? ORDER BY id "
                        + "LIMIT ? OFFSET ?",
                MAPPER, likePattern(keyword), limit, offset);
    }

    /**
     * 启用药品总数（端点 5 分页 total）。
     *
     * @param keyword 名称关键字（可空）
     * @return 总数
     */
    public long countEnabled(String keyword) {
        Long total;
        if (keyword == null || keyword.isBlank()) {
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drug_catalog WHERE enabled = 1", Long.class);
        } else {
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drug_catalog WHERE enabled = 1 AND name LIKE ?",
                    Long.class, likePattern(keyword));
        }
        return total == null ? 0L : total;
    }

    private static String likePattern(String keyword) {
        String escaped = keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
