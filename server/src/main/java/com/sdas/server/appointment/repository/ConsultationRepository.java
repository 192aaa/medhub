package com.sdas.server.appointment.repository;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.model.converter.ConsultationStatusConverter;
import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.doctor.model.converter.ConsultationTimeoutStatusConverter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * 图文问诊单持久化（JdbcTemplate）。表结构来源：data-model.md 2.8（002）与 2.2 扩展列（003）。
 * UNIQUE(appointment_id) 保证幂等；接诊以 accepted_at IS NULL 作为 CAS 条件。
 */
@Repository
public class ConsultationRepository {

    private static final ConsultationStatusConverter CONV = new ConsultationStatusConverter();

    private static final ConsultationTimeoutStatusConverter TIMEOUT_CONV =
            new ConsultationTimeoutStatusConverter();

    private static final RowMapper<Consultation> MAPPER = (rs, rowNum) -> new Consultation(
            rs.getLong("id"), rs.getLong("appointment_id"), rs.getLong("patient_id"),
            rs.getLong("doctor_id"), rs.getLong("department_id"), rs.getString("complaint"),
            rs.getString("description"), rs.getString("summary"),
            CONV.convertToEntityAttribute(rs.getString("status")),
            toLdt(rs.getTimestamp("accepted_at")), nullableLong(rs, "accepted_by_doctor_id"),
            TIMEOUT_CONV.convertToEntityAttribute(rs.getString("timeout_status")),
            toLdt(rs.getTimestamp("timeout_handled_at")), nullableLong(rs, "transferred_to_doctor_id"),
            toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")),
            rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造问诊单仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ConsultationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param consultation 问诊单 @return 新主键 */
    public long insert(Consultation consultation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO consultation (appointment_id, patient_id, doctor_id, department_id, "
                            + "complaint, description, summary, status, created_at, updated_at, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, consultation.appointmentId());
            ps.setLong(2, consultation.patientId());
            ps.setLong(3, consultation.doctorId());
            ps.setLong(4, consultation.departmentId());
            ps.setString(5, consultation.complaint());
            ps.setString(6, consultation.description());
            ps.setString(7, consultation.summary());
            ps.setString(8, CONV.convertToDatabaseColumn(consultation.status()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain consultation id");
        }
        return key.longValue();
    }

    /** @param id 主键 @return 问诊单 */
    public Optional<Consultation> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM consultation WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /** @param appointmentId 关联挂号单 @return 是否已存在问诊单 */
    public boolean existsByAppointmentId(long appointmentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM consultation WHERE appointment_id = ?", Integer.class,
                appointmentId);
        return count != null && count > 0;
    }

    /**
     * 按归属医生与状态查询问诊单（待接诊 / 已接诊列表）。
     *
     * @param doctorId 归属医生
     * @param status   问诊单状态
     * @return 问诊单列表（按创建时间倒序）
     */
    public List<Consultation> findByDoctorIdAndStatus(long doctorId, ConsultationStatus status) {
        return jdbcTemplate.query(
                "SELECT * FROM consultation WHERE doctor_id = ? AND status = ? ORDER BY created_at DESC",
                MAPPER, doctorId, CONV.convertToDatabaseColumn(status));
    }

    /**
     * 接诊（状态 CAS：PENDING → IN_PROGRESS 且 accepted_at IS NULL）。
     *
     * @param consultationId 问诊单主键
     * @param doctorId       接诊医生
     * @return 影响行数（0=并发/重复接诊冲突）
     */
    public int accept(long consultationId, long doctorId) {
        return jdbcTemplate.update(
                "UPDATE consultation SET status = ?, accepted_at = NOW(), accepted_by_doctor_id = ? "
                        + "WHERE id = ? AND status = ? AND accepted_at IS NULL",
                CONV.convertToDatabaseColumn(ConsultationStatus.IN_PROGRESS), doctorId,
                consultationId, CONV.convertToDatabaseColumn(ConsultationStatus.PENDING));
    }

    /**
     * 标记接诊超时（幂等：timeout_status = NONE 为条件）。
     *
     * @param consultationId 问诊单主键
     * @return 影响行数
     */
    public int markTimeout(long consultationId) {
        return jdbcTemplate.update(
                "UPDATE consultation SET timeout_status = ?, timeout_handled_at = NOW() "
                        + "WHERE id = ? AND timeout_status = ?",
                TIMEOUT_CONV.convertToDatabaseColumn(ConsultationTimeoutStatus.ADMIN_NOTIFIED),
                consultationId,
                TIMEOUT_CONV.convertToDatabaseColumn(ConsultationTimeoutStatus.NONE));
    }

    /**
     * 查询超时未处理的接诊单（供定时任务扫描）。
     *
     * @param acceptedBefore 接诊时间早于该时刻
     * @return 超时候选列表
     */
    public List<Consultation> findTimeoutCandidates(LocalDateTime acceptedBefore) {
        return jdbcTemplate.query(
                "SELECT * FROM consultation WHERE status = ? AND accepted_at IS NOT NULL "
                        + "AND accepted_at < ? AND timeout_status = ?",
                MAPPER,
                CONV.convertToDatabaseColumn(ConsultationStatus.IN_PROGRESS),
                Timestamp.valueOf(acceptedBefore),
                TIMEOUT_CONV.convertToDatabaseColumn(ConsultationTimeoutStatus.NONE));
    }

    /**
     * 按患者查询最近若干条已完成问诊（AI健康咨询病历上下文，只读）。
     *
     * <p>来源：specs/005-ai-health-consultation/plan.md M2。只读扩展，不修改表结构。</p>
     *
     * @param patientId 患者主键
     * @param status    目标状态（通常 COMPLETED）
     * @param limit     条数上限
     * @return 问诊列表（按创建时间倒序）
     */
    public List<Consultation> findRecentCompletedByPatientId(long patientId,
                                                             ConsultationStatus status, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM consultation WHERE patient_id = ? AND status = ? "
                        + "ORDER BY created_at DESC, id DESC LIMIT ?",
                MAPPER, patientId, CONV.convertToDatabaseColumn(status), limit);
    }

    /**
     * 按主键批量查询问诊单（AI健康咨询上下文，只读）。
     *
     * @param ids 主键列表
     * @return 问诊单列表
     */
    public List<Consultation> findByIdsIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM consultation WHERE id IN (" + placeholders + ")",
                MAPPER, ids.toArray());
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
