package com.sdas.server.healthconsultation.support;

import com.sdas.server.config.EmergencyRule;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 紧急症状规则匹配（M5，纯函数式）。
 *
 * <p>归一化（NFKC + 去空白 + 小写）后做关键词包含匹配；多规则命中取 {@code level} 最高，
 * 同级按配置声明顺序（列表下标）稳定排序。</p>
 */
@Component
public class EmergencyRuleMatcher {

    /**
     * 匹配紧急规则。
     *
     * @param text  患者输入
     * @param rules 生效规则表
     * @return 命中的最高级别规则
     */
    public Optional<EmergencyRule> match(String text, List<EmergencyRule> rules) {
        if (text == null || text.isBlank() || rules == null || rules.isEmpty()) {
            return Optional.empty();
        }
        String normalized = normalize(text);
        return java.util.stream.IntStream.range(0, rules.size())
                .mapToObj(index -> new Indexed(rules.get(index), index))
                .filter(indexed -> indexed.rule().keywords() != null
                        && !indexed.rule().keywords().isEmpty())
                .filter(indexed -> indexed.rule().keywords().stream()
                        .filter(keyword -> keyword != null && !keyword.isBlank())
                        .anyMatch(keyword -> normalized.contains(normalize(keyword))))
                .min(Comparator.<Indexed>comparingInt(indexed -> indexed.rule().levelWeight())
                        .reversed()
                        .thenComparingInt(Indexed::index))
                .map(Indexed::rule);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    /** 规则 + 声明下标（用于同级别稳定排序）。 */
    private record Indexed(EmergencyRule rule, int index) {
    }
}
