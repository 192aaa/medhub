package com.sdas.server.doctor.support;

import com.sdas.server.doctor.service.TreatmentDecisionClient;
import com.sdas.server.identity.support.IdentityTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 医生接诊集成测试基类。
 *
 * <p>复用 001 的 {@link IdentityTestSupport}（Testcontainers MySQL 8 + Redis + MockMvc），
 * 沿用 {@code IDENTITY_TEST_USE_LOCAL_INFRA=true} 本机基础设施开关与 Flyway 强制迁移。</p>
 *
 * <p>006：统一 {@link TreatmentDecisionClient} 为 Mock Bean，并提供问诊单/对话/药品夹具；
 * 主键固定高位段（501/1001/901），全部幂等写入，便于多次执行与并行套件共存。</p>
 */
public abstract class DoctorTestSupport extends IdentityTestSupport {

    /** 夹具问诊单主键。 */
    protected static final long FIXTURE_CONSULTATION_ID = 501L;

    /** 夹具患者主键。 */
    protected static final long FIXTURE_PATIENT_ID = 1001L;

    /** 夹具医生主键（与 {@code DoctorContractFixtures.seedDoctor} 一致）。 */
    protected static final long FIXTURE_DOCTOR_ID = 101L;

    /** 夹具科室主键。 */
    protected static final long FIXTURE_DEPARTMENT_ID = 1L;

    /** 夹具挂号单主键。 */
    protected static final long FIXTURE_APPOINTMENT_ID = 501L;

    /** 夹具排班主键。 */
    protected static final long FIXTURE_SCHEDULE_ID = 501L;

    /** 明文 "password" 的 BCrypt 哈希（与 V6 种子一致，仅测试）。 */
    private static final String PASSWORD_HASH =
            "$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm";

    /** 006 模型客户端 Mock：契约/集成测试按需给定时长与返回内容。 */
    @MockBean
    protected TreatmentDecisionClient treatmentDecisionClient;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 预置一张 IN_PROGRESS 问诊单及其外键链（科室/医生/排班/患者/挂号单）。
     *
     * @param consultationId 问诊单主键
     * @param patientId      患者主键
     * @param doctorId       接诊医生主键
     */
    protected void seedConsultationInProgress(long consultationId, long patientId, long doctorId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT IGNORE INTO department (id, name, enabled) VALUES (?, ?, 1)",
                FIXTURE_DEPARTMENT_ID, "内科");
        jdbcTemplate.update("INSERT IGNORE INTO doctor (id, department_id, name, title, hospital, "
                        + "online_status) VALUES (?, ?, '张*', '主任医师', '示例医院', 'ONLINE')",
                doctorId, FIXTURE_DEPARTMENT_ID);
        jdbcTemplate.update("INSERT IGNORE INTO doctor_schedule (id, doctor_id, work_date, period, "
                        + "slots_total, slots_booked) VALUES (?, ?, ?, 'AM', 20, 1)",
                FIXTURE_SCHEDULE_ID, doctorId, LocalDate.now().plusDays(1));
        jdbcTemplate.update("INSERT IGNORE INTO patient_account (id, phone_hash, phone_cipher, "
                        + "phone_mask, password_hash, auth_status, account_status, registered_at, "
                        + "verified_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, '138****8000', ?, 'VERIFIED', 'ACTIVE', ?, ?, ?, ?)",
                patientId, "b".repeat(64), new byte[] {1, 2, 3}, PASSWORD_HASH, now, now, now, now);
        jdbcTemplate.update("INSERT IGNORE INTO appointment (id, patient_id, doctor_id, "
                        + "department_id, schedule_id, appointment_date, period, fee_amount, "
                        + "payment_status, appointment_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'AM', 50.00, 'PAID', 'COMPLETED')",
                FIXTURE_APPOINTMENT_ID, patientId, doctorId, FIXTURE_DEPARTMENT_ID,
                FIXTURE_SCHEDULE_ID, LocalDate.now());
        jdbcTemplate.update("INSERT IGNORE INTO consultation (id, appointment_id, patient_id, "
                        + "doctor_id, department_id, complaint, description, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS')",
                consultationId, FIXTURE_APPOINTMENT_ID, patientId, doctorId,
                FIXTURE_DEPARTMENT_ID, "咳嗽伴咳痰三天", "夜间加重，无发热");
    }

    /**
     * 预置问诊单的状态（用于状态限制契约用例）。
     *
     * @param consultationId 问诊单主键
     * @param status         目标状态（PENDING/REPLIED/COMPLETED/CANCELLED）
     */
    protected void seedConsultationStatus(long consultationId, String status) {
        jdbcTemplate.update("UPDATE consultation SET status = ? WHERE id = ?", status,
                consultationId);
    }

    /**
     * 预置患者对话消息（内容按顺序追加，先清空该问诊单既有夹具消息保证幂等）。
     *
     * @param consultationId 问诊单主键
     * @param contents       患者消息内容
     */
    protected void seedDialog(long consultationId, String... contents) {
        jdbcTemplate.update("DELETE FROM consultation_message WHERE consultation_id = ?",
                consultationId);
        for (String content : contents) {
            jdbcTemplate.update("INSERT INTO consultation_message (consultation_id, sender_type, "
                            + "sender_id, content) VALUES (?, 'PATIENT', ?, ?)",
                    consultationId, FIXTURE_PATIENT_ID, content);
        }
    }

    /** 预置药品目录（复用 003 契约夹具的高位主键 901）。 */
    protected void seedDrugs() {
        jdbcTemplate.update("INSERT IGNORE INTO drug_catalog (id, name, spec, manufacturer, "
                        + "dosage_form, unit, prescription_only, enabled, source) "
                        + "VALUES (901, '阿莫西林胶囊', '0.25g*20', '示例制药', '胶囊', '盒', 1, 1, "
                        + "'PRESET')");
    }
}
