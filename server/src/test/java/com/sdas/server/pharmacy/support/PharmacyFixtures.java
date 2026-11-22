package com.sdas.server.pharmacy.support;

import com.sdas.server.doctor.entity.PrescriptionItem;
import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.doctor.support.PrescriptionCanonicalizer;
import com.sdas.server.doctor.support.PrescriptionSigningService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 药店端测试夹具（T026/T031）：全部语句幂等，并占用独立高位 ID 段
 * （科室 11 / 医生 1101 / 排班 1301 / 患者 3301 / 预约 1401 / 问诊 1701 / 处方 17001 / 药品 1901），
 * 避免与 003 的 1 / 101 / 701 / 901 冲突。
 *
 * <p>「已签名处方」按真实规则生成：签名原文经
 * {@link PrescriptionCanonicalizer} 计算后由 {@link PrescriptionSigningService} 签名，
 * {@code qr_code} 写 {@code v1} 令牌 URL —— 因此验真端点的内容签名复验与令牌校验都是真链路，
 * 不是桩数据。</p>
 */
public class PharmacyFixtures {

    /** 与 003 医生种子一致的口令明文（仅测试）。 */
    public static final String PASSWORD = "password";

    /** 明文 password 的 BCrypt 哈希（与 V6/V8 种子一致）。 */
    public static final String PASSWORD_HASH =
            "$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm";

    /** 夹具处方主键。 */
    public static final long PRESCRIPTION_ID = 17001L;

    /** 夹具问诊单主键。 */
    public static final long CONSULTATION_ID = 1701L;

    /** 夹具医生主键。 */
    public static final long DOCTOR_ID = 1101L;

    /** 夹具患者主键。 */
    public static final long PATIENT_ID = 3301L;

    /** 夹具药品主键。 */
    public static final long DRUG_ID = 1901L;

    private static final long DEPARTMENT_ID = 11L;

    private static final long SCHEDULE_ID = 1301L;

    private static final long APPOINTMENT_ID = 1401L;

    private static final int PHARMACY_ID_ACTIVE = 8101;

    private static final int PHARMACY_ID_DISABLED = 8102;

    private final JdbcTemplate jdbcTemplate;

    private final PrescriptionSigningService signingService;

    private final PrescriptionQrTokenService tokenService;

    /**
     * 构造夹具。
     *
     * @param jdbcTemplate   JDBC 模板
     * @param signingService 处方签名服务
     * @param tokenService   二维码令牌服务
     */
    public PharmacyFixtures(JdbcTemplate jdbcTemplate, PrescriptionSigningService signingService,
                            PrescriptionQrTokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.signingService = signingService;
        this.tokenService = tokenService;
    }

    /**
     * 幂等预置启用账号（主键 8101）。
     *
     * @return 药店主键
     */
    public long seedActivePharmacy() {
        return seedPharmacy(PHARMACY_ID_ACTIVE, "pharmacy_fixture_active", "王*", "ACTIVE");
    }

    /**
     * 幂等预置禁用账号（主键 8102）。
     *
     * @return 药店主键
     */
    public long seedDisabledPharmacy() {
        return seedPharmacy(PHARMACY_ID_DISABLED, "pharmacy_fixture_disabled", "李*", "DISABLED");
    }

    /**
     * 幂等写入药店账号（先按主键删除再插入，保证可重复执行）。
     *
     * @param id       药店主键
     * @param username 登录名
     * @param nameMask 经办人脱敏名
     * @param status   ACTIVE / DISABLED
     * @return 药店主键
     */
    public long seedPharmacy(long id, String username, String nameMask, String status) {
        jdbcTemplate.update("DELETE FROM prescription_dispense_record WHERE pharmacy_id = ?", id);
        jdbcTemplate.update("DELETE FROM pharmacy_account WHERE id = ?", id);
        jdbcTemplate.update(
                "INSERT INTO pharmacy_account (id, username, password_hash, name_mask, "
                        + "pharmacy_name, status) VALUES (?, ?, ?, ?, ?, ?)",
                id, username, new BCryptPasswordEncoder().encode(PASSWORD), nameMask,
                "示例大药房（测试店）", status);
        return id;
    }

    /**
     * 预置一张已签名处方（含患者/预约/问诊/药品完整外键链）。
     *
     * @param status     处方状态（{@code DRAFT} 时不签名、{@code qr_code} 为 null）
     * @param createdAt  创建与签名时间（精确到秒）
     * @param validUntil 有效期截止
     * @return 处方主键
     */
    public long seedSignedPrescription(PrescriptionStatus status, LocalDateTime createdAt,
                                       LocalDateTime validUntil) {
        cleanUpPrescription();
        seedPatientChain();
        List<PrescriptionItem> items = List.of(new PrescriptionItem(null, PRESCRIPTION_ID, DRUG_ID,
                "0.25g*20", "口服，一次 2 粒，一日 3 次", "青霉素过敏者禁用", null));
        String signature = null;
        String qrCode = null;
        LocalDateTime signedAt = null;
        if (status != PrescriptionStatus.DRAFT) {
            signature = signingService.sign(PrescriptionCanonicalizer.canonicalize(
                    PRESCRIPTION_ID, DOCTOR_ID, CONSULTATION_ID, items, createdAt));
            qrCode = tokenService.linkOf(tokenService.issue(PRESCRIPTION_ID, validUntil));
            signedAt = createdAt;
        }
        jdbcTemplate.update(
                "INSERT INTO prescription (id, consultation_id, doctor_id, patient_id, status, "
                        + "signature, qr_code, watermark, signed_at, valid_until, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PRESCRIPTION_ID, CONSULTATION_ID, DOCTOR_ID, PATIENT_ID, status.name(), signature,
                qrCode, "示例医院 电子处方", toTimestamp(signedAt), toTimestamp(validUntil),
                Timestamp.valueOf(createdAt));
        jdbcTemplate.update(
                "INSERT INTO prescription_item (prescription_id, drug_id, spec, dosage, caution) "
                        + "VALUES (?, ?, ?, ?, ?)",
                PRESCRIPTION_ID, DRUG_ID, "0.25g*20", "口服，一次 2 粒，一日 3 次", "青霉素过敏者禁用");
        return PRESCRIPTION_ID;
    }

    /**
     * 取夹具处方的验真链接（即库内 {@code qr_code}）。
     *
     * @return 验真链接
     */
    public String qrCodeOfFixture() {
        return jdbcTemplate.queryForObject(
                "SELECT qr_code FROM prescription WHERE id = ?", String.class, PRESCRIPTION_ID);
    }

    /**
     * 取夹具处方对应的 12 位验真码。
     *
     * @return 验真码
     */
    public String verifyCodeOfFixture() {
        return tokenService.verifyCode(tokenService.extractToken(qrCodeOfFixture()));
    }

    /** 清空药店账号与核销记录（测试收尾用）。 */
    public void cleanUp() {
        jdbcTemplate.update("DELETE FROM prescription_dispense_record");
        jdbcTemplate.update("DELETE FROM pharmacy_account");
    }

    /**
     * 清空夹具处方及其外键链。
     *
     * <p>临时关闭外键检查：共享测试库中可能残留其它套件写入的引用行（如 {@code payment_record}），
     * 按业务顺序删除仍可能被阻断；夹具只操作自己的高位 ID 段，因此这里以「删干净」优先。
     * 关闭后立即恢复，避免污染连接池中的其它使用方。</p>
     */
    public void cleanUpPrescription() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            jdbcTemplate.update("DELETE FROM prescription_item WHERE prescription_id = ?",
                    PRESCRIPTION_ID);
            jdbcTemplate.update("DELETE FROM prescription WHERE id = ?", PRESCRIPTION_ID);
            jdbcTemplate.update("DELETE FROM payment_record WHERE appointment_id = ?", APPOINTMENT_ID);
            jdbcTemplate.update("DELETE FROM consultation WHERE id = ?", CONSULTATION_ID);
            jdbcTemplate.update("DELETE FROM appointment WHERE id = ?", APPOINTMENT_ID);
            jdbcTemplate.update("DELETE FROM doctor_schedule WHERE id = ?", SCHEDULE_ID);
            jdbcTemplate.update("DELETE FROM drug_catalog WHERE id = ?", DRUG_ID);
            jdbcTemplate.update("DELETE FROM doctor WHERE id = ?", DOCTOR_ID);
            jdbcTemplate.update("DELETE FROM patient_account WHERE id = ?", PATIENT_ID);
            jdbcTemplate.update("DELETE FROM department WHERE id = ?", DEPARTMENT_ID);
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    /** @return dev 种子里的药店账号数量（test/prod 应为 0） */
    public long countPharmacyAccounts() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pharmacy_account",
                Long.class);
        return count == null ? 0L : count;
    }

    private void seedPatientChain() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO department (id, name, enabled) VALUES (?, ?, 1)",
                DEPARTMENT_ID, "测试内科");
        jdbcTemplate.update("INSERT INTO doctor (id, department_id, name, title, hospital, "
                        + "online_status) VALUES (?, ?, ?, ?, ?, ?)",
                DOCTOR_ID, DEPARTMENT_ID, "张*", "主任医师", "示例医院", "ONLINE");
        jdbcTemplate.update("INSERT INTO doctor_schedule (id, doctor_id, work_date, period, "
                        + "slots_total, slots_booked) VALUES (?, ?, ?, ?, 20, 1)",
                SCHEDULE_ID, DOCTOR_ID, LocalDate.now().plusDays(1), "AM");
        jdbcTemplate.update("INSERT INTO patient_account (id, phone_hash, phone_cipher, "
                        + "phone_mask, password_hash, auth_status, account_status, registered_at, "
                        + "verified_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'VERIFIED', 'ACTIVE', ?, ?, ?, ?)",
                PATIENT_ID, "a".repeat(64), new byte[] {1, 2, 3}, "138****8000", PASSWORD_HASH,
                now, now, now, now);
        jdbcTemplate.update("INSERT INTO drug_catalog (id, name, spec, manufacturer, dosage_form, "
                        + "unit, prescription_only, enabled, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 1, 1, 'PRESET')",
                DRUG_ID, "阿莫西林胶囊", "0.25g*20", "示例制药", "胶囊", "盒");
        jdbcTemplate.update("INSERT INTO appointment (id, patient_id, doctor_id, department_id, "
                        + "schedule_id, appointment_date, period, fee_amount, payment_status, "
                        + "appointment_status) VALUES (?, ?, ?, ?, ?, ?, ?, 50.00, 'PAID', ?)",
                APPOINTMENT_ID, PATIENT_ID, DOCTOR_ID, DEPARTMENT_ID, SCHEDULE_ID,
                LocalDate.now(), "AM", "COMPLETED");
        jdbcTemplate.update("INSERT INTO consultation (id, appointment_id, patient_id, doctor_id, "
                        + "department_id, complaint, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                CONSULTATION_ID, APPOINTMENT_ID, PATIENT_ID, DOCTOR_ID, DEPARTMENT_ID,
                "咳嗽三天", "COMPLETED");
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
