package com.sdas.server.identity.repository;

import com.sdas.server.identity.entity.HospitalIdentityWhitelist;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 医院白名单只读仓储（D2：本功能无任何写方法、无外部同步）。
 *
 * <p>表结构来源：specs/001-patient-identity/data-model.md 3.5。</p>
 */
@Repository
public class HospitalIdentityWhitelistRepository {

    private static final RowMapper<HospitalIdentityWhitelist> MAPPER =
            (rs, rowNum) -> new HospitalIdentityWhitelist(
                    rs.getLong("id"), rs.getString("real_name"), rs.getString("id_card_hash"),
                    rs.getBytes("id_card_cipher"), rs.getBoolean("enabled"),
                    rs.getString("source"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("updated_at").toLocalDateTime(), rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造白名单仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public HospitalIdentityWhitelistRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 仅按 enabled=1 且姓名 + 证件号摘要精确匹配查询单行。
     *
     * @param realName   姓名（不做 trim / 模糊匹配）
     * @param idCardHash 证件号 HMAC 摘要
     * @return 匹配记录，未命中时为空
     */
    public Optional<HospitalIdentityWhitelist> findEnabled(String realName, String idCardHash) {
        return jdbcTemplate.query(
                "SELECT * FROM hospital_identity_whitelist "
                        + "WHERE enabled = 1 AND real_name = ? AND id_card_hash = ? LIMIT 1",
                MAPPER, realName, idCardHash).stream().findFirst();
    }
}
