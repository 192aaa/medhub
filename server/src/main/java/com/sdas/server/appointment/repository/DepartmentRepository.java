package com.sdas.server.appointment.repository;

import com.sdas.server.appointment.entity.Department;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 科室持久化（JdbcTemplate）。表结构来源：data-model.md 2.1。 */
@Repository
public class DepartmentRepository {

    private static final RowMapper<Department> MAPPER = (rs, rowNum) -> new Department(
            rs.getLong("id"), rs.getString("name"), rs.getString("description"),
            rs.getString("icon_url"), rs.getInt("sort_order"), rs.getBoolean("enabled"),
            toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造科室仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DepartmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return 全部启用科室，按 sort_order 升序 */
    public List<Department> findAllEnabled() {
        return jdbcTemplate.query(
                "SELECT * FROM department WHERE enabled = 1 ORDER BY sort_order", MAPPER);
    }

    /** @return 全部科室（含停用，供导诊降级使用） */
    public List<Department> findAll() {
        return jdbcTemplate.query("SELECT * FROM department ORDER BY sort_order", MAPPER);
    }

    /** @param id 科室主键 @return 科室 */
    public Optional<Department> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM department WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /**
     * 按名称查询启用科室（AI健康咨询紧急预警的挂号科室预填，只读）。
     *
     * <p>{@code department} 表无 code 列且 005 只读依赖其结构，故规则按名称精确匹配。
     * 存在同名科室时取 sort_order 最小者。</p>
     *
     * @param name 科室名称
     * @return 科室
     */
    public Optional<Department> findEnabledByName(String name) {
        return jdbcTemplate.query(
                "SELECT * FROM department WHERE name = ? AND enabled = 1 "
                        + "ORDER BY sort_order, id LIMIT 1", MAPPER, name)
                .stream().findFirst();
    }

    /**
     * 按主键批量查询科室（AI健康咨询病历上下文，只读）。
     *
     * @param ids 主键列表
     * @return 科室列表
     */
    public List<Department> findByIdsIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM department WHERE id IN (" + placeholders + ")",
                MAPPER, ids.toArray());
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
