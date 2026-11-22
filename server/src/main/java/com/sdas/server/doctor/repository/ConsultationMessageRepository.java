package com.sdas.server.doctor.repository;

import com.sdas.server.doctor.entity.ConsultationMessage;
import com.sdas.server.doctor.model.converter.MessageTypeConverter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 接诊对话消息持久化（JdbcTemplate）。表结构来源：data-model.md 2.3。 */
@Repository
public class ConsultationMessageRepository {

    private static final MessageTypeConverter CONV = new MessageTypeConverter();

    private static final RowMapper<ConsultationMessage> MAPPER = (rs, rowNum) -> new ConsultationMessage(
            rs.getLong("id"), rs.getLong("consultation_id"),
            CONV.convertToEntityAttribute(rs.getString("sender_type")),
            nullableLong(rs, "sender_id"), rs.getString("content"),
            toLdt(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造对话消息仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ConsultationMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param message 消息 @return 新主键 */
    public long insert(ConsultationMessage message) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO consultation_message (consultation_id, sender_type, sender_id, content) "
                            + "VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, message.consultationId());
            ps.setString(2, CONV.convertToDatabaseColumn(message.senderType()));
            if (message.senderId() == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, message.senderId());
            }
            ps.setString(4, message.content());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain consultation_message id");
        }
        return key.longValue();
    }

    /** @param consultationId 问诊单主键 @return 对话消息（按时间正序） */
    public List<ConsultationMessage> findByConsultationId(long consultationId) {
        return jdbcTemplate.query(
                "SELECT * FROM consultation_message WHERE consultation_id = ? ORDER BY created_at ASC, id ASC",
                MAPPER, consultationId);
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
