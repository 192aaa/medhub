package com.sdas.server.identity.repository;

import com.sdas.server.identity.entity.PatientAccount;
import com.sdas.server.identity.model.converter.AccountStatusConverter;
import com.sdas.server.identity.model.converter.AuthStatusConverter;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 患者账户持久化（JdbcTemplate）。
 *
 * <p>表结构来源：specs/001-patient-identity/data-model.md 3.1。</p>
 */
@Repository
public class PatientAccountRepository {

    private static final AuthStatusConverter AUTH_CONVERTER = new AuthStatusConverter();

    private static final AccountStatusConverter ACCOUNT_CONVERTER = new AccountStatusConverter();

    private static final RowMapper<PatientAccount> MAPPER = (rs, rowNum) -> new PatientAccount(
            rs.getLong("id"), rs.getString("phone_hash"), rs.getBytes("phone_cipher"),
            rs.getString("phone_mask"), rs.getString("password_hash"),
            AUTH_CONVERTER.convertToEntityAttribute(rs.getString("auth_status")),
            ACCOUNT_CONVERTER.convertToEntityAttribute(rs.getString("account_status")),
            rs.getTimestamp("registered_at").toLocalDateTime(),
            toLocalDateTime(rs.getTimestamp("verified_at")),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(), rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造账户仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PatientAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按手机号摘要查询账户。
     *
     * @param phoneHash 手机号 HMAC 摘要
     * @return 账户，不存在时为空
     */
    public Optional<PatientAccount> findByPhoneHash(String phoneHash) {
        return jdbcTemplate.query(
                "SELECT * FROM patient_account WHERE phone_hash = ?", MAPPER, phoneHash)
                .stream().findFirst();
    }

    /**
     * 按患者主键查询账户（端点 7/8 用统一 JWT 身份取数）。
     *
     * @param patientId 患者主键
     * @return 账户，不存在时为空
     */
    public Optional<PatientAccount> findById(long patientId) {
        return jdbcTemplate.query(
                "SELECT * FROM patient_account WHERE id = ?", MAPPER, patientId)
                .stream().findFirst();
    }

    /**
     * 插入账户并返回自增主键。
     *
     * @param account 待插入账户
     * @return 新账户主键
     */
    public long insert(PatientAccount account) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO patient_account (phone_hash, phone_cipher, phone_mask, "
                            + "password_hash, auth_status, account_status, registered_at, "
                            + "verified_at, created_at, updated_at, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, account.phoneHash());
            statement.setBytes(2, account.phoneCipher());
            statement.setString(3, account.phoneMask());
            statement.setString(4, account.passwordHash());
            statement.setString(5, AUTH_CONVERTER.convertToDatabaseColumn(account.authStatus()));
            statement.setString(6, ACCOUNT_CONVERTER.convertToDatabaseColumn(account.accountStatus()));
            statement.setTimestamp(7, java.sql.Timestamp.valueOf(account.registeredAt()));
            statement.setTimestamp(8, account.verifiedAt() == null ? null
                    : java.sql.Timestamp.valueOf(account.verifiedAt()));
            statement.setTimestamp(9, java.sql.Timestamp.valueOf(account.createdAt()));
            statement.setTimestamp(10, java.sql.Timestamp.valueOf(account.updatedAt()));
            statement.setInt(11, account.version());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain patient account id");
        }
        return key.longValue();
    }

    /**
     * 更新认证状态（带 version 乐观锁）。
     *
     * @param patientId  患者主键
     * @param status     新认证状态
     * @param verifiedAt 认证时间
     * @param version    当前版本
     * @return 受影响行数
     */
    public int updateAuthStatus(long patientId, String status, LocalDateTime verifiedAt,
                                int version) {
        return jdbcTemplate.update(
                "UPDATE patient_account SET auth_status = ?, verified_at = ?, "
                        + "updated_at = NOW(), version = version + 1 "
                        + "WHERE id = ? AND version = ?",
                status, verifiedAt == null ? null : java.sql.Timestamp.valueOf(verifiedAt),
                patientId, version);
    }

    /**
     * 更新密码哈希（带 version 乐观锁）。
     *
     * @param patientId    患者主键
     * @param passwordHash 新 BCrypt 哈希
     * @param version      当前版本
     * @return 受影响行数
     */
    public int updatePasswordHash(long patientId, String passwordHash, int version) {
        return jdbcTemplate.update(
                "UPDATE patient_account SET password_hash = ?, updated_at = NOW(), "
                        + "version = version + 1 WHERE id = ? AND version = ?",
                passwordHash, patientId, version);
    }

    private static LocalDateTime toLocalDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
