package com.sdas.server.healthconsultation.repository;

import com.sdas.server.healthconsultation.entity.HealthSession;
import com.sdas.server.healthconsultation.model.SessionStatus;
import com.sdas.server.healthconsultation.model.converter.SessionStatusConverter;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 咨询会话持久化（JdbcTemplate）。
 *
 * <p>表结构来源：specs/005-ai-health-consultation/data-model.md 2.1。
 * 所有读写方法均强制携带 {@code patient_id} 条件，越权与不存在同码（{@code SESSION_NOT_FOUND}）。</p>
 */
@Repository
public class HealthSessionRepository {

    private static final SessionStatusConverter CONV = new SessionStatusConverter();

    private static final String COLUMNS = "id, patient_id, title, last_active_at, message_count, "
            + "status, created_at, updated_at, version";

    private static final RowMapper<HealthSession> MAPPER = (rs, rowNum) -> new HealthSession(
            rs.getLong("id"),
            rs.getLong("patient_id"),
            rs.getString("title"),
            toLdt(rs.getTimestamp("last_active_at")),
            rs.getInt("message_count"),
            CONV.convertToEntityAttribute(rs.getString("status")),
            toLdt(rs.getTimestamp("created_at")),
            toLdt(rs.getTimestamp("updated_at")),
            rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造会话仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public HealthSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 新建会话。
     *
     * @param patientId    患者主键
     * @param title        标题
     * @param lastActiveAt 最近活跃时间
     * @return 新主键
     */
    public long insert(long patientId, String title, LocalDateTime lastActiveAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ai_health_session (patient_id, title, last_active_at, "
                            + "message_count, status) VALUES (?, ?, ?, 0, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, patientId);
            ps.setString(2, title);
            ps.setTimestamp(3, Timestamp.valueOf(lastActiveAt));
            ps.setString(4, CONV.convertToDatabaseColumn(SessionStatus.ACTIVE));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain ai health session id");
        }
        return key.longValue();
    }

    /**
     * 按患者与会话定位（未删除）。
     *
     * @param sessionId 会话主键
     * @param patientId 患者主键
     * @return 会话
     */
    public Optional<HealthSession> findByIdAndPatientId(long sessionId, long patientId) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM ai_health_session "
                        + "WHERE id = ? AND patient_id = ? AND status = 'ACTIVE'",
                MAPPER, sessionId, patientId).stream().findFirst();
    }

    /**
     * 分页查询患者会话（最近活跃倒序）。
     *
     * @param patientId 患者主键
     * @param offset    偏移
     * @param limit     条数
     * @return 会话列表
     */
    public List<HealthSession> findByPatientId(long patientId, int offset, int limit) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM ai_health_session "
                        + "WHERE patient_id = ? AND status = 'ACTIVE' "
                        + "ORDER BY last_active_at DESC, id DESC LIMIT ? OFFSET ?",
                MAPPER, patientId, limit, offset);
    }

    /**
     * 统计患者未删除会话数。
     *
     * @param patientId 患者主键
     * @return 条数
     */
    public long countByPatientId(long patientId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_health_session "
                + "WHERE patient_id = ? AND status = 'ACTIVE'", Long.class, patientId);
        return count == null ? 0L : count;
    }

    /**
     * 重命名会话（乐观锁）。
     *
     * @param sessionId       会话主键
     * @param patientId       患者主键
     * @param title           新标题
     * @param expectedVersion 期望版本
     * @return 影响行数
     */
    public int rename(long sessionId, long patientId, String title, int expectedVersion) {
        return jdbcTemplate.update("UPDATE ai_health_session SET title = ?, version = version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND patient_id = ? "
                        + "AND status = 'ACTIVE' AND version = ?",
                title, sessionId, patientId, expectedVersion);
    }

    /**
     * 逻辑删除单个会话。
     *
     * @param sessionId 会话主键
     * @param patientId 患者主键
     * @return 影响行数
     */
    public int markDeleted(long sessionId, long patientId) {
        return jdbcTemplate.update("UPDATE ai_health_session SET status = 'DELETED', "
                        + "version = version + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND patient_id = ? AND status = 'ACTIVE'",
                sessionId, patientId);
    }

    /**
     * 逻辑删除患者全部会话。
     *
     * @param patientId 患者主键
     * @return 影响行数
     */
    public int markAllDeleted(long patientId) {
        return jdbcTemplate.update("UPDATE ai_health_session SET status = 'DELETED', "
                        + "version = version + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE patient_id = ? AND status = 'ACTIVE'",
                patientId);
    }

    /**
     * 更新活跃时间与消息计数。
     *
     * @param sessionId        会话主键
     * @param messageCountDelta 消息数增量
     * @param lastActiveAt     最近活跃时间
     * @return 影响行数
     */
    public int touch(long sessionId, int messageCountDelta, LocalDateTime lastActiveAt) {
        return jdbcTemplate.update("UPDATE ai_health_session SET message_count = message_count + ?, "
                        + "last_active_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                messageCountDelta, Timestamp.valueOf(lastActiveAt), sessionId);
    }

    /**
     * 统计会话消息数（用于上限校验）。
     *
     * @param sessionId 会话主键
     * @return 消息数
     */
    public int currentMessageCount(long sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT message_count FROM ai_health_session WHERE id = ?", Integer.class, sessionId);
        return count == null ? 0 : count;
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
