package com.sdas.server.appointment.repository;

import com.sdas.server.appointment.entity.AiTriageSession;
import com.sdas.server.appointment.model.TriageResultStatus;
import com.sdas.server.appointment.model.converter.TriageResultStatusConverter;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** AI 导诊会话持久化（JdbcTemplate）。表结构来源：data-model.md 2.10。 */
@Repository
public class AiTriageSessionRepository {

    private static final TriageResultStatusConverter CONV = new TriageResultStatusConverter();

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造导诊会话仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public AiTriageSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param session 导诊会话 @return 新主键 */
    public long insert(AiTriageSession session) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ai_triage_session (patient_id, complaint, recommended_department_ids, "
                            + "model_used, elapsed_ms, status, kb_version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            if (session.patientId() == null) {
                ps.setNull(1, java.sql.Types.BIGINT);
            } else {
                ps.setLong(1, session.patientId());
            }
            ps.setString(2, session.complaint());
            ps.setString(3, session.recommendedDepartmentIds());
            ps.setString(4, session.modelUsed());
            ps.setLong(5, session.elapsedMs());
            ps.setString(6, CONV.convertToDatabaseColumn(session.status()));
            ps.setString(7, session.kbVersion());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain triage session id");
        }
        return key.longValue();
    }
}
