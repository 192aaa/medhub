package com.sdas.server.appointment.repository;

import com.sdas.server.appointment.entity.DoctorSchedule;
import com.sdas.server.appointment.model.SchedulePeriod;
import com.sdas.server.appointment.model.converter.SchedulePeriodConverter;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 医生排班持久化（JdbcTemplate）。表结构来源：data-model.md 2.3。 */
@Repository
public class DoctorScheduleRepository {

    private static final SchedulePeriodConverter CONVERTER = new SchedulePeriodConverter();

    private static final RowMapper<DoctorSchedule> MAPPER = (rs, rowNum) -> new DoctorSchedule(
            rs.getLong("id"), rs.getLong("doctor_id"),
            toDate(rs.getDate("work_date")), CONVERTER.convertToEntityAttribute(rs.getString("period")),
            rs.getInt("slots_total"), rs.getInt("slots_booked"),
            toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造排班仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DoctorScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param id 排班主键 @return 排班 */
    public Optional<DoctorSchedule> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM doctor_schedule WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /** @param doctorId 医生主键 @return 该医生全部排班 */
    public List<DoctorSchedule> findByDoctorId(long doctorId) {
        return jdbcTemplate.query(
                "SELECT * FROM doctor_schedule WHERE doctor_id = ? ORDER BY work_date, period", MAPPER,
                doctorId);
    }

    private static LocalDate toDate(java.sql.Date d) {
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
