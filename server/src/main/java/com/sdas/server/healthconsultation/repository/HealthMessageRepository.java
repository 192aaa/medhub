package com.sdas.server.healthconsultation.repository;

import com.sdas.server.healthconsultation.entity.HealthMessage;
import com.sdas.server.healthconsultation.model.MessageRole;
import com.sdas.server.healthconsultation.model.StreamStatus;
import com.sdas.server.healthconsultation.model.converter.MessageRoleConverter;
import com.sdas.server.healthconsultation.model.converter.StreamStatusConverter;
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
 * 咨询消息持久化（JdbcTemplate）。
 *
 * <p>表结构来源：specs/005-ai-health-consultation/data-model.md 2.2。
 * 消息历史按主键正序返回；热窗口重建只取 {@code COMPLETED} 消息（R-06）。</p>
 */
@Repository
public class HealthMessageRepository {

    private static final MessageRoleConverter ROLE_CONV = new MessageRoleConverter();

    private static final StreamStatusConverter STREAM_CONV = new StreamStatusConverter();

    private static final String COLUMNS = "id, session_id, role, content, disclaimer, emergency, "
            + "rule_code, stream_status, model_used, created_at";

    private static final RowMapper<HealthMessage> MAPPER = (rs, rowNum) -> new HealthMessage(
            rs.getLong("id"),
            rs.getLong("session_id"),
            ROLE_CONV.convertToEntityAttribute(rs.getString("role")),
            rs.getString("content"),
            rs.getString("disclaimer"),
            rs.getBoolean("emergency"),
            rs.getString("rule_code"),
            STREAM_CONV.convertToEntityAttribute(rs.getString("stream_status")),
            rs.getString("model_used"),
            toLdt(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造消息仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public HealthMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入消息。
     *
     * @param message 消息（{@code id} 忽略）
     * @return 新主键
     */
    public long insert(HealthMessage message) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ai_health_message (session_id, role, content, disclaimer, emergency, "
                            + "rule_code, stream_status, model_used) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, message.sessionId());
            ps.setString(2, ROLE_CONV.convertToDatabaseColumn(message.role()));
            ps.setString(3, message.content());
            ps.setString(4, message.disclaimer());
            ps.setBoolean(5, message.emergency());
            ps.setString(6, message.ruleCode());
            ps.setString(7, STREAM_CONV.convertToDatabaseColumn(message.streamStatus()));
            ps.setString(8, message.modelUsed());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain ai health message id");
        }
        return key.longValue();
    }

    /**
     * 分页查询会话消息（按主键正序）。
     *
     * @param sessionId 会话主键
     * @param offset    偏移
     * @param limit     条数
     * @return 消息列表
     */
    public List<HealthMessage> findBySessionId(long sessionId, int offset, int limit) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM ai_health_message "
                        + "WHERE session_id = ? ORDER BY id ASC LIMIT ? OFFSET ?",
                MAPPER, sessionId, limit, offset);
    }

    /**
     * 统计会话消息数。
     *
     * @param sessionId 会话主键
     * @return 条数
     */
    public long countBySessionId(long sessionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_health_message WHERE session_id = ?", Long.class, sessionId);
        return count == null ? 0L : count;
    }

    /**
     * 取会话最后一条消息（列表页摘要）。
     *
     * @param sessionId 会话主键
     * @return 最后一条消息
     */
    public Optional<HealthMessage> findLastBySessionId(long sessionId) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM ai_health_message "
                        + "WHERE session_id = ? ORDER BY id DESC LIMIT 1", MAPPER, sessionId)
                .stream().findFirst();
    }

    /**
     * 取最近 N 条已完成消息（用于热窗口重建，返回正序）。
     *
     * @param sessionId 会话主键
     * @param limit     条数
     * @return 消息列表
     */
    public List<HealthMessage> findRecentCompleted(long sessionId, int limit) {
        return jdbcTemplate.query("SELECT * FROM (SELECT " + COLUMNS + " FROM ai_health_message "
                        + "WHERE session_id = ? AND stream_status = 'COMPLETED' "
                        + "ORDER BY id DESC LIMIT ?) t ORDER BY id ASC",
                MAPPER, sessionId, limit);
    }

    /**
     * 更新流式完成状态。
     *
     * @param messageId 消息主键
     * @param status    新状态
     * @return 影响行数
     */
    public int markStreamStatus(long messageId, StreamStatus status) {
        return jdbcTemplate.update("UPDATE ai_health_message SET stream_status = ? WHERE id = ?",
                STREAM_CONV.convertToDatabaseColumn(status), messageId);
    }

    /**
     * 物理删除会话全部消息（清空历史 / 删除会话）。
     *
     * @param sessionId 会话主键
     * @return 影响行数
     */
    public int deleteBySessionId(long sessionId) {
        return jdbcTemplate.update("DELETE FROM ai_health_message WHERE session_id = ?", sessionId);
    }

    /**
     * 物理删除患者全部消息（清空全部历史）。
     *
     * @param patientId 患者主键
     * @return 影响行数
     */
    public int deleteByPatientId(long patientId) {
        return jdbcTemplate.update("DELETE m FROM ai_health_message m "
                + "JOIN ai_health_session s ON m.session_id = s.id WHERE s.patient_id = ?", patientId);
    }

    /**
     * 统计会话内指定角色的消息数。
     *
     * @param sessionId 会话主键
     * @param role      角色
     * @return 条数
     */
    public long countBySessionIdAndRole(long sessionId, MessageRole role) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_health_message "
                        + "WHERE session_id = ? AND role = ?", Long.class, sessionId,
                ROLE_CONV.convertToDatabaseColumn(role));
        return count == null ? 0L : count;
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
