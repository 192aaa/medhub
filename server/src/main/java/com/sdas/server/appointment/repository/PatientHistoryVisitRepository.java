package com.sdas.server.appointment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 患者历史就诊记录持久化（JdbcTemplate，只读，用于复诊资格判定）。表结构来源：data-model.md 2.5。 */
@Repository
public class PatientHistoryVisitRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造历史就诊记录仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PatientHistoryVisitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 判定患者是否存在历史就诊记录（复诊资格依据之一）。
     *
     * @param patientId 患者主键
     * @return 存在记录则返回 true
     */
    public boolean existsByPatientId(long patientId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM patient_history_visit WHERE patient_id = ?", Integer.class,
                patientId);
        return count != null && count > 0;
    }
}
