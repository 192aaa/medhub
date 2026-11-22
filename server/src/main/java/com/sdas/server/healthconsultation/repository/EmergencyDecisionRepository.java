package com.sdas.server.healthconsultation.repository;

import com.sdas.server.healthconsultation.entity.EmergencyDecision;
import com.sdas.server.healthconsultation.model.converter.EmergencyVerdictConverter;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 紧急判定留痕持久化（JdbcTemplate）。
 *
 * <p>表结构来源：specs/005-ai-health-consultation/data-model.md 2.3。
 * 只存规则码与结论，<b>禁止写入病情明文</b>（宪法原则 IV）。</p>
 */
@Repository
public class EmergencyDecisionRepository {

    private static final EmergencyVerdictConverter CONV = new EmergencyVerdictConverter();

    private static final RowMapper<EmergencyDecision> MAPPER = (rs, rowNum) -> new EmergencyDecision(
            rs.getLong("id"),
            rs.getLong("session_id"),
            rs.getObject("message_id") == null ? null : rs.getLong("message_id"),
            rs.getString("rule_code"),
            CONV.convertToEntityAttribute(rs.getString("model_verdict")),
            rs.getBoolean("warned"),
            toLdt(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造判定留痕仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public EmergencyDecisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入判定记录。
     *
     * @param decision 判定记录（{@code id}/{@code createdAt} 忽略）
     * @return 新主键
     */
    public long insert(EmergencyDecision decision) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ai_health_emergency_decision (session_id, message_id, rule_code, "
                            + "model_verdict, warned) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, decision.sessionId());
            if (decision.messageId() == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, decision.messageId());
            }
            ps.setString(3, decision.ruleCode());
            ps.setString(4, CONV.convertToDatabaseColumn(decision.modelVerdict()));
            ps.setBoolean(5, decision.warned());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain emergency decision id");
        }
        return key.longValue();
    }

    /**
     * 回填写手消息主键。
     *
     * @param decisionId 判定记录主键
     * @param messageId  助手消息主键
     * @return 影响行数
     */
    public int fillMessageId(long decisionId, long messageId) {
        return jdbcTemplate.update(
                "UPDATE ai_health_emergency_decision SET message_id = ? WHERE id = ?",
                messageId, decisionId);
    }

    /**
     * 查询会话内的判定记录（测试与回溯用）。
     *
     * @param sessionId 会话主键
     * @return 记录列表
     */
    public List<EmergencyDecision> findBySessionId(long sessionId) {
        return jdbcTemplate.query("SELECT * FROM ai_health_emergency_decision "
                + "WHERE session_id = ? ORDER BY id ASC", MAPPER, sessionId);
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
