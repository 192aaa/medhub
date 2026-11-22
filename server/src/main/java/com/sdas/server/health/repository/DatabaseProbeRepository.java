package com.sdas.server.health.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 数据库持久化探针，仅负责执行 {@code SELECT 1}。
 *
 * <p>宪法原则 II：Repository 只负责持久化访问，业务编排在 Service。</p>
 */
@Repository
public class DatabaseProbeRepository {

    private static final String SELECT_ONE = "SELECT 1";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造数据库探针。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public DatabaseProbeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行连通性查询。
     *
     * @return 查询结果，正常为 1
     */
    public Integer selectOne() {
        return jdbcTemplate.queryForObject(SELECT_ONE, Integer.class);
    }
}
