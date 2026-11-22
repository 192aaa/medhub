package com.sdas.server.healthconsultation.support;

import com.sdas.server.config.HealthProperties;
import com.sdas.server.healthconsultation.repository.KnowledgeRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 医学知识检索（M3 / R-05）。
 *
 * <p>规整化后取中文 2-gram 与 3-gram 作为候选词，一次 SQL 用 {@code IN}+{@code LIKE} 检索，
 * 命中计数与 Top-K 排序在仓储内完成。空命中返回空列表，由 prompt 要求模型声明依据有限。</p>
 */
@Component
public class KnowledgeRetriever {

    /** 候选词上限，避免 SQL 条件过长。 */
    private static final int MAX_CANDIDATES = 40;

    /** 中文常见虚词片段，参与检索只会放大噪声。 */
    private static final Set<String> STOPWORDS = Set.of(
            "的了", "是在", "我有", "我们", "这个", "那个", "可以", "什么", "怎么", "为什",
            "是不", "不是", "一下", "的话", "就是", "还是", "然后", "因为", "所以", "已经",
            "现在", "有点", "感觉", "觉得", "一个", "这些", "那些", "时候");

    private final KnowledgeRepository knowledgeRepository;

    private final HealthProperties properties;

    /**
     * 构造知识检索器。
     *
     * @param knowledgeRepository 知识只读仓储
     * @param properties          005 配置（Top-K 默认值）
     */
    public KnowledgeRetriever(KnowledgeRepository knowledgeRepository,
                              HealthProperties properties) {
        this.knowledgeRepository = knowledgeRepository;
        this.properties = properties;
    }

    /**
     * 按患者本轮描述检索知识。
     *
     * @param patientText 患者输入
     * @param topK        条数（≤0 时取配置默认值）
     * @return 命中列表（可能为空）
     */
    public List<KnowledgeHit> retrieve(String patientText, int topK) {
        if (patientText == null || patientText.isBlank()) {
            return List.of();
        }
        List<String> candidates = candidates(patientText);
        if (candidates.isEmpty()) {
            return List.of();
        }
        int limit = topK > 0 ? topK : properties.knowledgeTopK();
        return knowledgeRepository.searchByKeywords(candidates, limit);
    }

    private static List<String> candidates(String text) {
        String cleaned = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s，。！？、；：“”‘’（）【】《》…—·]", "");
        Set<String> grams = new LinkedHashSet<>();
        for (int n = 2; n <= 3; n++) {
            for (int i = 0; i + n <= cleaned.length(); i++) {
                String gram = cleaned.substring(i, i + n);
                if (!STOPWORDS.contains(gram)) {
                    grams.add(gram);
                }
            }
        }
        List<String> result = new ArrayList<>(grams);
        return result.size() > MAX_CANDIDATES ? result.subList(0, MAX_CANDIDATES) : result;
    }
}
