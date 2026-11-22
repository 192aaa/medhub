package com.sdas.server.appointment.repository;

import com.sdas.server.appointment.entity.DepartmentDiseaseKb;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 科室-疾病知识库持久化（JdbcTemplate，数据库预置只读）。表结构来源：data-model.md 2.4。 */
@Repository
public class DepartmentDiseaseKbRepository {

    private static final RowMapper<DepartmentDiseaseKb> MAPPER = (rs, rowNum) ->
            new DepartmentDiseaseKb(rs.getLong("id"), rs.getLong("department_id"),
                    rs.getString("disease_keyword"), rs.getString("disease_name"),
                    rs.getString("source"), rs.getString("kb_version"),
                    toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造科室-疾病知识库仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DepartmentDiseaseKbRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按症状关键词在疾病关键词上做双向模糊匹配，返回去重科室 ID（AI 推荐的名称无法命中时的兜底）。
     *
     * <p>双向匹配覆盖两种写法：主诉包含知识库关键词（如「咳嗽三天」命中「咳嗽」），
     * 或知识库关键词包含主诉片段。单向匹配会导致主诉越长越难命中。</p>
     *
     * @param keyword 症状关键词（通常为患者主诉）
     * @return 科室 ID 列表
     */
    public List<Long> findDepartmentIdsByKeyword(String keyword) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT department_id FROM department_disease_kb "
                        + "WHERE ? LIKE CONCAT('%', disease_keyword, '%') "
                        + "OR disease_keyword LIKE CONCAT('%', ?, '%')",
                Long.class, keyword, keyword);
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
