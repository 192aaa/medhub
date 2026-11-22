package com.sdas.server.identity.repository;

import com.sdas.server.identity.entity.PatientSession;
import com.sdas.server.identity.model.SessionStatus;
import com.sdas.server.identity.model.converter.SessionStatusConverter;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 会话与续期凭证持久化。
 *
 * <p>表结构来源：specs/001-patient-identity/data-model.md 3.4。</p>
 */
@Repository
public class PatientSessionRepository {

    private static final SessionStatusConverter CONVERTER = new SessionStatusConverter();

    private static final RowMapper<PatientSession> MAPPER = (rs, rowNum) -> new PatientSession(
            rs.getLong("id"), rs.getLong("patient_id"), rs.getString("refresh_token_hash"),
            CONVERTER.convertToEntityAttribute(rs.getString("session_status")),
            rs.getString("client_ip_hash"),
            rs.getTimestamp("expires_at").toLocalDateTime(),
            rs.getTimestamp("revoked_at") == null ? null
                    : rs.getTimestamp("revoked_at").toLocalDateTime(),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(), rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造会话仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PatientSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按续期凭证摘要查询会话。
     *
     * @param refreshTokenHash 摘要
     * @return 会话，不存在时为空
     */
    public Optional<PatientSession> findByRefreshTokenHash(String refreshTokenHash) {
        return jdbcTemplate.query(
                "SELECT * FROM patient_session WHERE refresh_token_hash = ?",
                MAPPER, refreshTokenHash).stream().findFirst();
    }

    /**
     * 插入会话并返回主键（主键写入 JWT sessionId）。
     *
     * @param session 待插入会话
     * @return 会话主键
     */
    public long insert(PatientSession session) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO patient_session (patient_id, refresh_token_hash, session_status, "
                            + "client_ip_hash, expires_at, revoked_at, created_at, updated_at, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, session.patientId());
            statement.setString(2, session.refreshTokenHash());
            statement.setString(3, CONVERTER.convertToDatabaseColumn(session.sessionStatus()));
            statement.setString(4, session.clientIpHash());
            statement.setTimestamp(5, java.sql.Timestamp.valueOf(session.expiresAt()));
            statement.setTimestamp(6, session.revokedAt() == null ? null
                    : java.sql.Timestamp.valueOf(session.revokedAt()));
            statement.setTimestamp(7, java.sql.Timestamp.valueOf(session.createdAt()));
            statement.setTimestamp(8, java.sql.Timestamp.valueOf(session.updatedAt()));
            statement.setInt(9, session.version());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain patient session id");
        }
        return key.longValue();
    }

    /**
     * 条件撤销单个会话（ACTIVE → REVOKED，带 version 乐观锁）。
     *
     * @param sessionId 会话主键
     * @param version   当前版本
     * @return 受影响行数，0 表示并发冲突
     */
    public int revoke(long sessionId, int version) {
        return jdbcTemplate.update(
                "UPDATE patient_session SET session_status = ?, revoked_at = NOW(), "
                        + "updated_at = NOW(), version = version + 1 "
                        + "WHERE id = ? AND session_status = ? AND version = ?",
                SessionStatus.REVOKED.name(), sessionId, SessionStatus.ACTIVE.name(), version);
    }

    /**
     * 按患者批量撤销全部有效会话（重置密码用）。
     *
     * @param patientId 患者主键
     * @return 受影响行数
     */
    public int revokeAllByPatientId(long patientId) {
        return jdbcTemplate.update(
                "UPDATE patient_session SET session_status = ?, revoked_at = NOW(), "
                        + "updated_at = NOW(), version = version + 1 "
                        + "WHERE patient_id = ? AND session_status = ?",
                SessionStatus.REVOKED.name(), patientId, SessionStatus.ACTIVE.name());
    }

    /**
     * 查询患者全部有效会话主键（撤销前调用，供写入 Redis 撤销标记）。
     *
     * @param patientId 患者主键
     * @return 有效会话主键列表
     */
    public List<Long> findActiveSessionIds(long patientId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM patient_session WHERE patient_id = ? AND session_status = ?",
                Long.class, patientId, SessionStatus.ACTIVE.name());
    }

    /**
     * 按会话主键查询（登出 / 轮换用）。
     *
     * @param sessionId 会话主键
     * @return 会话，不存在时为空
     */
    public Optional<PatientSession> findById(long sessionId) {
        return jdbcTemplate.query("SELECT * FROM patient_session WHERE id = ?",
                MAPPER, sessionId).stream().findFirst();
    }

    /**
     * 幂等撤销单个会话（重复登出时返回 0 仍视为成功）。
     *
     * @param sessionId 会话主键
     * @return 受影响行数
     */
    public int revokeById(long sessionId) {
        return jdbcTemplate.update(
                "UPDATE patient_session SET session_status = ?, revoked_at = NOW(), "
                        + "updated_at = NOW(), version = version + 1 "
                        + "WHERE id = ? AND session_status = ?",
                SessionStatus.REVOKED.name(), sessionId, SessionStatus.ACTIVE.name());
    }
}
