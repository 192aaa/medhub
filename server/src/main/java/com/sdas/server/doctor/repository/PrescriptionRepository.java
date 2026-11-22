package com.sdas.server.doctor.repository;

import com.sdas.server.doctor.entity.Prescription;
import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.doctor.model.converter.PrescriptionStatusConverter;
import java.sql.PreparedStatement;
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

/** 电子处方持久化（JdbcTemplate）。表结构来源：data-model.md 2.5。UNIQUE(consultation_id) 保证一张单一张处方。 */
@Repository
public class PrescriptionRepository {

    private static final PrescriptionStatusConverter CONV = new PrescriptionStatusConverter();

    private static final RowMapper<Prescription> MAPPER = (rs, rowNum) -> new Prescription(
            rs.getLong("id"), rs.getLong("consultation_id"), rs.getLong("doctor_id"),
            rs.getLong("patient_id"),
            CONV.convertToEntityAttribute(rs.getString("status")),
            rs.getString("signature"), rs.getString("qr_code"), rs.getString("watermark"),
            toLdt(rs.getTimestamp("signed_at")), toLdt(rs.getTimestamp("valid_until")),
            toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")),
            rs.getInt("version"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造处方仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PrescriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param prescription 处方 @return 新主键 */
    public long insert(Prescription prescription) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO prescription (consultation_id, doctor_id, patient_id, status, "
                            + "signature, qr_code, watermark, signed_at, valid_until, version, "
                            + "created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, COALESCE(?, NOW()))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, prescription.consultationId());
            ps.setLong(2, prescription.doctorId());
            ps.setLong(3, prescription.patientId());
            ps.setString(4, CONV.convertToDatabaseColumn(prescription.status()));
            ps.setString(5, prescription.signature());
            ps.setString(6, prescription.qrCode());
            ps.setString(7, prescription.watermark());
            ps.setTimestamp(8, toTimestamp(prescription.signedAt()));
            ps.setTimestamp(9, toTimestamp(prescription.validUntil()));
            // 显式写入创建时间：签名原文含 createdAt，必须与库内值一致才能复验
            ps.setTimestamp(10, toTimestamp(prescription.createdAt()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain prescription id");
        }
        return key.longValue();
    }

    /** @param id 主键 @return 处方 */
    public Optional<Prescription> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM prescription WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /** @param consultationId 问诊单主键 @return 处方（一张单仅一张） */
    public Optional<Prescription> findByConsultationId(long consultationId) {
        return jdbcTemplate.query(
                "SELECT * FROM prescription WHERE consultation_id = ?", MAPPER, consultationId)
                .stream().findFirst();
    }

    /**
     * 患者处方分页查询（排除草稿，按开方时间倒序）。
     *
     * @param patientId 患者主键
     * @param offset    偏移量
     * @param limit     条数
     * @return 处方列表
     */
    public List<Prescription> findByPatientId(long patientId, int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM prescription WHERE patient_id = ? AND status <> 'DRAFT' "
                        + "ORDER BY signed_at DESC, id DESC LIMIT ? OFFSET ?",
                MAPPER, patientId, limit, offset);
    }

    /**
     * 患者处方总数（排除草稿）。
     *
     * @param patientId 患者主键
     * @return 总数
     */
    public long countByPatientId(long patientId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE patient_id = ? AND status <> 'DRAFT'",
                Long.class, patientId);
        return count == null ? 0L : count;
    }

    /**
     * 按患者归属查询处方详情（排除草稿；越权与不存在同码）。
     *
     * @param id        处方主键
     * @param patientId 患者主键
     * @return 处方
     */
    public Optional<Prescription> findByIdAndPatientId(long id, long patientId) {
        return jdbcTemplate.query(
                "SELECT * FROM prescription WHERE id = ? AND patient_id = ? AND status <> 'DRAFT'",
                MAPPER, id, patientId).stream().findFirst();
    }

    /**
     * 处方签名生效（DRAFT → SIGNED）。
     *
     * @param prescriptionId 处方主键
     * @param signature      签名摘要
     * @param qrCode         二维码内容
     * @param watermark      水印内容
     * @param signedAt       签名时间
     * @param validUntil     有效期截止
     * @return 影响行数
     */
    public int sign(long prescriptionId, String signature, String qrCode, String watermark,
                    LocalDateTime signedAt, LocalDateTime validUntil) {
        return jdbcTemplate.update(
                "UPDATE prescription SET status = ?, signature = ?, qr_code = ?, watermark = ?, "
                        + "signed_at = ?, valid_until = ? WHERE id = ?",
                CONV.convertToDatabaseColumn(com.sdas.server.doctor.model.PrescriptionStatus.SIGNED),
                signature, qrCode, watermark,
                Timestamp.valueOf(signedAt), Timestamp.valueOf(validUntil), prescriptionId);
    }

    /**
     * 按问诊单批量查询指定状态的处方（AI健康咨询病历上下文，只读）。
     *
     * <p>来源：specs/005-ai-health-consultation/plan.md M2。只读扩展，不修改表结构。</p>
     *
     * @param consultationIds 问诊单主键列表
     * @param status          目标状态（通常 SIGNED）
     * @return 处方列表
     */
    public List<Prescription> findByConsultationIdInAndStatus(List<Long> consultationIds,
                                                             PrescriptionStatus status) {
        if (consultationIds == null || consultationIds.isEmpty()) {
            return List.of();
        }
        String placeholders =
                String.join(",", java.util.Collections.nCopies(consultationIds.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM prescription WHERE consultation_id IN ("
                        + placeholders + ") AND status = ?",
                MAPPER, append(consultationIds.toArray(), CONV.convertToDatabaseColumn(status)));
    }

    private static Object[] append(Object[] args, Object last) {
        Object[] result = new Object[args.length + 1];
        System.arraycopy(args, 0, result, 0, args.length);
        result[args.length] = last;
        return result;
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
