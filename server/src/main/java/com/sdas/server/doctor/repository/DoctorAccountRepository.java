package com.sdas.server.doctor.repository;

import com.sdas.server.doctor.entity.DoctorAccount;
import com.sdas.server.doctor.model.converter.DoctorAccountStatusConverter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 医生账号持久化（JdbcTemplate）。表结构来源：data-model.md 2.1。 */
@Repository
public class DoctorAccountRepository {

    private static final DoctorAccountStatusConverter CONV = new DoctorAccountStatusConverter();

    private static final RowMapper<DoctorAccount> MAPPER = (rs, rowNum) -> new DoctorAccount(
            rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
            rs.getLong("doctor_id"), rs.getString("name_mask"),
            CONV.convertToEntityAttribute(rs.getString("status")),
            toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")),
            rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造医生账号仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DoctorAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param username 登录名 @return 医生账号 */
    public Optional<DoctorAccount> findByUsername(String username) {
        return jdbcTemplate.query("SELECT * FROM doctor_account WHERE username = ?", MAPPER, username)
                .stream().findFirst();
    }

    /** @param id 主键 @return 医生账号 */
    public Optional<DoctorAccount> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM doctor_account WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
