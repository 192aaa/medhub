package com.sdas.server.pharmacy.repository;

import com.sdas.server.pharmacy.entity.PharmacyAccount;
import com.sdas.server.pharmacy.model.converter.PharmacyAccountStatusConverter;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 药店账号持久化（JdbcTemplate）。表结构来源：data-model.md 2.1。 */
@Repository
public class PharmacyAccountRepository {

    private static final PharmacyAccountStatusConverter CONV =
            new PharmacyAccountStatusConverter();

    private static final RowMapper<PharmacyAccount> MAPPER = (rs, rowNum) -> new PharmacyAccount(
            rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
            rs.getString("name_mask"), rs.getString("pharmacy_name"),
            CONV.convertToEntityAttribute(rs.getString("status")),
            toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")),
            rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造药店账号仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PharmacyAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param id 主键 @return 药店账号 */
    public Optional<PharmacyAccount> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM pharmacy_account WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /** @param username 登录名 @return 药店账号 */
    public Optional<PharmacyAccount> findByUsername(String username) {
        return jdbcTemplate.query("SELECT * FROM pharmacy_account WHERE username = ?", MAPPER,
                username).stream().findFirst();
    }

    /** @param account 账号 @return 新主键 */
    public long insert(PharmacyAccount account) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO pharmacy_account (username, password_hash, name_mask, "
                            + "pharmacy_name, status) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, account.username());
            ps.setString(2, account.passwordHash());
            ps.setString(3, account.nameMask());
            ps.setString(4, account.pharmacyName());
            ps.setString(5, CONV.convertToDatabaseColumn(account.status()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain pharmacy account id");
        }
        return key.longValue();
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
