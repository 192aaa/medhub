package com.sdas.server.appointment.repository;

import com.sdas.server.appointment.entity.ConsultationImage;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 问诊图片持久化（JdbcTemplate）。表结构来源：data-model.md 2.9。 */
@Repository
public class ConsultationImageRepository {

    private static final RowMapper<ConsultationImage> MAPPER = (rs, rowNum) -> new ConsultationImage(
            rs.getLong("id"), rs.getLong("consultation_id"), rs.getString("image_url"),
            rs.getString("content_type"), rs.getLong("size_bytes"),
            toLdt(rs.getTimestamp("created_at")), toLdt(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造问诊图片仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ConsultationImageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入图片记录（上传时 consultation_id 可为空，提交问诊单时再关联）。
     *
     * @param image 图片记录
     * @return 新主键
     */
    public long insert(ConsultationImage image) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO consultation_image (consultation_id, image_url, content_type, "
                            + "size_bytes, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            if (image.consultationId() == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, image.consultationId());
            }
            ps.setString(2, image.imageUrl());
            ps.setString(3, image.contentType());
            ps.setLong(4, image.sizeBytes());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain consultation image id");
        }
        return key.longValue();
    }

    /**
     * 将上传图片关联到问诊单（按 URL 匹配尚未关联的图片）。
     *
     * @param consultationId 问诊单主键
     * @param urls           图片 URL 列表
     * @return 被关联的图片数量
     */
    public int linkByUrls(long consultationId, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (String url : urls) {
            total += jdbcTemplate.update(
                    "UPDATE consultation_image SET consultation_id = ? WHERE image_url = ? "
                            + "AND consultation_id IS NULL", consultationId, url);
        }
        return total;
    }

    /**
     * 查询某问诊单的全部图片。
     *
     * @param consultationId 问诊单主键
     * @return 图片列表
     */
    public List<ConsultationImage> findByConsultationId(long consultationId) {
        return jdbcTemplate.query(
                "SELECT * FROM consultation_image WHERE consultation_id = ? ORDER BY id", MAPPER,
                consultationId);
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
