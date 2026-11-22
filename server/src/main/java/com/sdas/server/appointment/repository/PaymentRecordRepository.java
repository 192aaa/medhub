package com.sdas.server.appointment.repository;

import com.sdas.server.appointment.entity.PaymentRecord;
import com.sdas.server.appointment.model.PaymentChannel;
import com.sdas.server.appointment.model.PaymentRecordStatus;
import com.sdas.server.appointment.model.converter.PaymentChannelConverter;
import com.sdas.server.appointment.model.converter.PaymentRecordStatusConverter;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 支付流水持久化（JdbcTemplate）。表结构来源：data-model.md 2.7。UNIQUE(appointment_id) 保证幂等。 */
@Repository
public class PaymentRecordRepository {

    private static final PaymentChannelConverter CHANNEL_CONV = new PaymentChannelConverter();
    private static final PaymentRecordStatusConverter STATUS_CONV = new PaymentRecordStatusConverter();

    private static final RowMapper<PaymentRecord> MAPPER = (rs, rowNum) -> new PaymentRecord(
            rs.getLong("id"), rs.getLong("appointment_id"),
            CHANNEL_CONV.convertToEntityAttribute(rs.getString("channel")),
            rs.getBigDecimal("amount"), rs.getString("out_trade_no"),
            STATUS_CONV.convertToEntityAttribute(rs.getString("status")),
            toLdt(rs.getTimestamp("paid_at")), toLdt(rs.getTimestamp("created_at")),
            toLdt(rs.getTimestamp("updated_at")), rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造支付流水仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PaymentRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param record 支付流水 @return 新主键 */
    public long insert(PaymentRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO payment_record (appointment_id, channel, amount, out_trade_no, "
                            + "status, paid_at, created_at, updated_at, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, record.appointmentId());
            ps.setString(2, CHANNEL_CONV.convertToDatabaseColumn(record.channel()));
            ps.setBigDecimal(3, record.amount());
            ps.setString(4, record.outTradeNo());
            ps.setString(5, STATUS_CONV.convertToDatabaseColumn(record.status()));
            ps.setTimestamp(6, record.paidAt() == null ? null : Timestamp.valueOf(record.paidAt()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain payment record id");
        }
        return key.longValue();
    }

    /** @param appointmentId 挂号单 @return 支付流水 */
    public Optional<PaymentRecord> findByAppointmentId(long appointmentId) {
        return jdbcTemplate.query(
                "SELECT * FROM payment_record WHERE appointment_id = ?", MAPPER, appointmentId)
                .stream().findFirst();
    }

    /** @param appointmentId 挂号单 @return 是否存在流水 */
    public boolean existsByAppointmentId(long appointmentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM payment_record WHERE appointment_id = ?", Integer.class,
                appointmentId);
        return count != null && count > 0;
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
