package com.sdas.server.doctor.repository;

import com.sdas.server.doctor.entity.AiDecisionSession;
import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.model.TreatmentDecisionStatus;
import com.sdas.server.doctor.model.TreatmentTriggerType;
import com.sdas.server.doctor.model.converter.DegradedReasonConverter;
import com.sdas.server.doctor.model.converter.TreatmentDecisionStatusConverter;
import com.sdas.server.doctor.model.converter.TreatmentTriggerTypeConverter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * AI 辅助决策会话持久化（JdbcTemplate）。
 *
 * <p>表结构来源：003 data-model.md 2.7（基础列）+ 006 data-model.md 2.1（V11 扩展列）。</p>
 */
@Repository
public class AiDecisionSessionRepository {

    private static final TreatmentDecisionStatusConverter STATUS_CONV =
            new TreatmentDecisionStatusConverter();

    private static final TreatmentTriggerTypeConverter TRIGGER_CONV =
            new TreatmentTriggerTypeConverter();

    private static final DegradedReasonConverter REASON_CONV = new DegradedReasonConverter();

    private static final RowMapper<AiDecisionSession> MAPPER = (rs, rowNum) ->
            new AiDecisionSession(
                    rs.getLong("id"),
                    rs.getLong("consultation_id"),
                    rs.getLong("doctor_id"),
                    rs.getString("input_summary"),
                    rs.getString("output"),
                    rs.getString("model_used"),
                    STATUS_CONV.convertToEntityAttribute(rs.getString("status")),
                    nullableInt(rs, "elapsed_ms"),
                    toLdt(rs.getTimestamp("created_at")),
                    TRIGGER_CONV.convertToEntityAttribute(rs.getString("trigger_type")),
                    rs.getString("dialog_version"),
                    rs.getString("result_json"),
                    REASON_CONV.convertToEntityAttribute(rs.getString("degraded_reason")),
                    nullableInt(rs, "applied_item_count"),
                    toLdt(rs.getTimestamp("adopted_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造 AI 决策会话仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public AiDecisionSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入一条辅助记录。
     *
     * @param session 会话
     * @return 新主键
     */
    public long insert(AiDecisionSession session) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ai_decision_session (consultation_id, doctor_id, trigger_type, "
                            + "dialog_version, input_summary, output, result_json, model_used, "
                            + "status, degraded_reason, elapsed_ms, applied_item_count, adopted_at, "
                            + "created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, NOW()))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, session.consultationId());
            ps.setLong(2, session.doctorId());
            ps.setString(3, TRIGGER_CONV.convertToDatabaseColumn(session.triggerType()));
            ps.setString(4, session.dialogVersion());
            ps.setString(5, session.inputSummary());
            ps.setString(6, session.output());
            ps.setString(7, session.resultJson());
            ps.setString(8, session.modelUsed());
            ps.setString(9, STATUS_CONV.convertToDatabaseColumn(session.status()));
            ps.setString(10, REASON_CONV.convertToDatabaseColumn(session.degradedReason()));
            if (session.elapsedMs() == null) {
                ps.setNull(11, java.sql.Types.INTEGER);
            } else {
                ps.setInt(11, session.elapsedMs());
            }
            if (session.appliedItemCount() == null) {
                ps.setNull(12, java.sql.Types.INTEGER);
            } else {
                ps.setInt(12, session.appliedItemCount());
            }
            ps.setTimestamp(13, toTimestamp(session.adoptedAt()));
            ps.setTimestamp(14, toTimestamp(session.createdAt()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain ai_decision_session id");
        }
        return key.longValue();
    }

    /**
     * 查询该问诊单由该医生发起的最近一条记录。
     *
     * @param consultationId 问诊单
     * @param doctorId       医生
     * @return 记录
     */
    public Optional<AiDecisionSession> findLatestByConsultationIdAndDoctorId(long consultationId,
                                                                            long doctorId) {
        return jdbcTemplate.query(
                "SELECT * FROM ai_decision_session WHERE consultation_id = ? AND doctor_id = ? "
                        + "ORDER BY id DESC LIMIT 1", MAPPER, consultationId, doctorId)
                .stream().findFirst();
    }

    /**
     * 按对话指纹查询可复用记录（006 幂等）。
     *
     * @param consultationId 问诊单
     * @param doctorId       医生
     * @param dialogVersion  对话指纹
     * @param since          复用窗口起点
     * @return 可复用记录
     */
    public Optional<AiDecisionSession> findReusable(long consultationId, long doctorId,
                                                    String dialogVersion, LocalDateTime since) {
        return jdbcTemplate.query(
                "SELECT * FROM ai_decision_session WHERE consultation_id = ? AND doctor_id = ? "
                        + "AND dialog_version = ? AND status = 'SUCCESS' AND created_at >= ? "
                        + "ORDER BY id DESC LIMIT 1",
                MAPPER, consultationId, doctorId, dialogVersion, toTimestamp(since))
                .stream().findFirst();
    }

    /**
     * 回填采纳数量与时间（端点 4）。
     *
     * @param id              记录主键
     * @param appliedItemCount 采纳条数
     * @param adoptedAt       采纳时间
     * @return 受影响行数
     */
    public int markAdopted(long id, int appliedItemCount, LocalDateTime adoptedAt) {
        return jdbcTemplate.update(
                "UPDATE ai_decision_session SET applied_item_count = ?, adopted_at = ? WHERE id = ?",
                appliedItemCount, toTimestamp(adoptedAt), id);
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLdt(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
