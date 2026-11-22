package com.sdas.server.healthconsultation.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 医生姓名只读查询（AI健康咨询病历摘要，M2）。
 *
 * <p>{@code doctor.name} 在 002 中已是脱敏姓名（禁止真实姓名），可直接用于摘要；
 * 本仓储只读，不修改 002/003 的既有仓储职责。</p>
 */
@Repository
public class DoctorNameRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造医生姓名仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DoctorNameRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按主键批量取医生脱敏姓名。
     *
     * @param ids 医生主键列表
     * @return 主键 → 姓名（缺失的键不出现在结果中）
     */
    public Map<Long, String> findNamesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Map<Long, String> result = new HashMap<>(ids.size());
        jdbcTemplate.query("SELECT id, name FROM doctor WHERE id IN (" + placeholders + ")",
                rs -> {
                    result.put(rs.getLong("id"), rs.getString("name"));
                }, ids.toArray());
        return result;
    }
}
