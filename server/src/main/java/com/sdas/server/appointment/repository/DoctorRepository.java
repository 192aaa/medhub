package com.sdas.server.appointment.repository;

import com.sdas.server.appointment.entity.Doctor;
import com.sdas.server.appointment.model.DoctorOnlineStatus;
import com.sdas.server.appointment.model.converter.DoctorOnlineStatusConverter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 医生持久化（JdbcTemplate）。表结构来源：data-model.md 2.2。 */
@Repository
public class DoctorRepository {

    private static final DoctorOnlineStatusConverter CONVERTER = new DoctorOnlineStatusConverter();

    private static final RowMapper<Doctor> MAPPER = (rs, rowNum) -> new Doctor(
            rs.getLong("id"), rs.getLong("department_id"), rs.getString("name"),
            rs.getString("title"), rs.getString("hospital"), rs.getInt("years_of_experience"),
            rs.getString("good_at"), rs.getBigDecimal("consultation_fee"),
            rs.getBigDecimal("rating"), rs.getInt("consultation_count"),
            CONVERTER.convertToEntityAttribute(rs.getString("online_status")),
            rs.getString("avatar_url"),
            toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造医生仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public DoctorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param id 医生主键 @return 医生 */
    public Optional<Doctor> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM doctor WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /** @return 全部医生（供导诊推荐映射） */
    public List<Doctor> findAll() {
        return jdbcTemplate.query("SELECT * FROM doctor ORDER BY id", MAPPER);
    }

    /**
     * 按科室与在线状态筛选。
     *
     * @param departmentId 科室
     * @param onlineStatus 在线状态
     * @return 医生列表
     */
    public List<Doctor> findByDepartmentIdAndOnlineStatus(long departmentId, String onlineStatus) {
        return jdbcTemplate.query(
                "SELECT * FROM doctor WHERE department_id = ? AND online_status = ? "
                        + "ORDER BY consultation_count DESC", MAPPER, departmentId, onlineStatus);
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
