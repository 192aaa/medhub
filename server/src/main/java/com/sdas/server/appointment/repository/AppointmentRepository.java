package com.sdas.server.appointment.repository;

import com.sdas.server.appointment.entity.Appointment;
import com.sdas.server.appointment.model.AppointmentStatus;
import com.sdas.server.appointment.model.PaymentStatus;
import com.sdas.server.appointment.model.SchedulePeriod;
import com.sdas.server.appointment.model.converter.AppointmentStatusConverter;
import com.sdas.server.appointment.model.converter.PaymentStatusConverter;
import com.sdas.server.appointment.model.converter.SchedulePeriodConverter;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 挂号单持久化（JdbcTemplate）。表结构来源：data-model.md 2.6。 */
@Repository
public class AppointmentRepository {

    private static final PaymentStatusConverter PAY_CONV = new PaymentStatusConverter();
    private static final AppointmentStatusConverter APPT_CONV = new AppointmentStatusConverter();
    private static final SchedulePeriodConverter PERIOD_CONV = new SchedulePeriodConverter();

    private static final RowMapper<Appointment> MAPPER = (rs, rowNum) -> new Appointment(
            rs.getLong("id"), rs.getLong("patient_id"), rs.getLong("doctor_id"),
            rs.getLong("department_id"), rs.getLong("schedule_id"),
            toDate(rs.getDate("appointment_date")), PERIOD_CONV.convertToEntityAttribute(rs.getString("period")),
            rs.getBigDecimal("fee_amount"), PAY_CONV.convertToEntityAttribute(rs.getString("payment_status")),
            APPT_CONV.convertToEntityAttribute(rs.getString("appointment_status")),
            rs.getString("patient_note"), toLdt(rs.getTimestamp("created_at")),
            toLdt(rs.getTimestamp("updated_at")), rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造挂号单仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public AppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param appointment 挂号单 @return 新主键 */
    public long insert(Appointment appointment) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO appointment (patient_id, doctor_id, department_id, schedule_id, "
                            + "appointment_date, period, fee_amount, payment_status, "
                            + "appointment_status, patient_note, created_at, updated_at, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, appointment.patientId());
            ps.setLong(2, appointment.doctorId());
            ps.setLong(3, appointment.departmentId());
            ps.setLong(4, appointment.scheduleId());
            ps.setDate(5, java.sql.Date.valueOf(appointment.appointmentDate()));
            ps.setString(6, PERIOD_CONV.convertToDatabaseColumn(appointment.period()));
            ps.setBigDecimal(7, appointment.feeAmount());
            ps.setString(8, PAY_CONV.convertToDatabaseColumn(appointment.paymentStatus()));
            ps.setString(9, APPT_CONV.convertToDatabaseColumn(appointment.appointmentStatus()));
            ps.setString(10, appointment.patientNote());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain appointment id");
        }
        return key.longValue();
    }

    /** @param id 主键 @return 挂号单 */
    public Optional<Appointment> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM appointment WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /** @param patientId 患者 @param scheduleId 排班 @return 同一患者的同排班挂号单（幂等判定） */
    public Optional<Appointment> findByPatientAndSchedule(long patientId, long scheduleId) {
        return jdbcTemplate.query(
                "SELECT * FROM appointment WHERE patient_id = ? AND schedule_id = ?", MAPPER,
                patientId, scheduleId).stream().findFirst();
    }

    /**
     * 支付成功后原子更新双状态列（带乐观锁）。
     *
     * @param id                主键
     * @param paymentStatus    新支付状态
     * @param appointmentStatus 新挂号单状态
     * @param version          当前版本
     * @return 受影响行数
     */
    public int updatePaymentStatus(long id, PaymentStatus paymentStatus,
                                   AppointmentStatus appointmentStatus, int version) {
        return jdbcTemplate.update(
                "UPDATE appointment SET payment_status = ?, appointment_status = ?, "
                        + "updated_at = NOW(), version = version + 1 "
                        + "WHERE id = ? AND version = ?",
                PAY_CONV.convertToDatabaseColumn(paymentStatus),
                APPT_CONV.convertToDatabaseColumn(appointmentStatus), id, version);
    }

    private static LocalDate toDate(java.sql.Date d) {
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
