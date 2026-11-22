package com.sdas.server.healthconsultation.repository;

import com.sdas.server.healthconsultation.support.KnowledgeHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 医学知识只读检索（{@code department_disease_kb}，002 表，R-05）。
 *
 * <p>本仓储只读，不新增/修改知识表结构；预置数据由 {@code V10} 写入。
 * 该表<b>无 enabled 列</b>，故检索不加启用条件（见 tasks.md T008 修订）。
 * 一次 SQL 用 {@code IN}+{@code LIKE} 拼装候选词，命中计数在内存完成。</p>
 */
@Repository
public class KnowledgeRepository {

    /** 单次检索的候选行上限，避免全表扫描与内存膨胀。 */
    private static final int CANDIDATE_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造知识检索仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public KnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按候选关键词检索 Top-K 知识点。
     *
     * @param keywords 候选关键词（2/3-gram，已去重）
     * @param topK     返回条数
     * @return 命中列表（命中词数降序，同分按主键升序）
     */
    public List<KnowledgeHit> searchByKeywords(List<String> keywords, int topK) {
        if (keywords == null || keywords.isEmpty() || topK <= 0) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, disease_keyword, disease_name, department_id, kb_version "
                        + "FROM department_disease_kb WHERE ");
        List<Object> args = new ArrayList<>(keywords.size());
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("disease_keyword LIKE ?");
            args.add("%" + keywords.get(i) + "%");
        }
        sql.append(" ORDER BY id ASC LIMIT ").append(CANDIDATE_LIMIT);

        List<Row> rows = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new Row(rs.getLong("id"), rs.getString("disease_keyword"),
                        rs.getString("disease_name"), rs.getLong("department_id"),
                        rs.getString("kb_version")),
                args.toArray());

        return rows.stream()
                .map(row -> row.toHit(keywords))
                .filter(hit -> !hit.matchedKeywords().isEmpty())
                .sorted(Comparator.comparingInt((KnowledgeHit hit) -> hit.matchedKeywords().size())
                        .reversed())
                .limit(topK)
                .toList();
    }

    /**
     * 统计指定来源与版本的条目数（迁移自检）。
     *
     * @param source    来源（PRESET）
     * @param kbVersion 知识版本
     * @return 条数
     */
    public long countBySourceAndKbVersion(String source, String kbVersion) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM department_disease_kb WHERE source = ? AND kb_version = ?",
                Long.class, source, kbVersion);
        return count == null ? 0L : count;
    }

    /** 候选行（含主键，用于稳定排序）。 */
    private record Row(long id, String keyword, String name, long departmentId, String kbVersion) {

        /**
         * 计算命中词并转换为对外模型。
         *
         * @param candidates 候选词
         * @return 命中项
         */
        KnowledgeHit toHit(List<String> candidates) {
            String normalizedKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
            List<String> matched = candidates.stream()
                    .filter(candidate -> matches(normalizedKeyword, candidate))
                    .distinct()
                    .toList();
            return new KnowledgeHit(name, departmentId, kbVersion, matched);
        }

        private static boolean matches(String normalizedKeyword, String candidate) {
            String c = candidate.toLowerCase(Locale.ROOT);
            return normalizedKeyword.contains(c) || c.contains(normalizedKeyword);
        }
    }
}
