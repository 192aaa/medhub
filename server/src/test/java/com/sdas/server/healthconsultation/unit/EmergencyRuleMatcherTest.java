package com.sdas.server.healthconsultation.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sdas.server.config.DefaultEmergencyRules;
import com.sdas.server.config.EmergencyRule;
import com.sdas.server.healthconsultation.support.EmergencyRuleMatcher;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 规则匹配。 */
class EmergencyRuleMatcherTest {

    private final EmergencyRuleMatcher matcher = new EmergencyRuleMatcher();

    @Test
    @DisplayName("关键词命中返回规则")
    void matchesKeyword() {
        var rule = matcher.match("胸口疼得厉害，喘不上气", DefaultEmergencyRules.RULES);
        assertThat(rule).isPresent();
        assertThat(rule.orElseThrow().code()).isEqualTo("CHEST_PAIN_DYSPNEA");
    }

    @Test
    @DisplayName("普通不适不命中")
    void noMatchForMildSymptom() {
        assertThat(matcher.match("有点困，想早点睡", DefaultEmergencyRules.RULES)).isEmpty();
    }

    @Test
    @DisplayName("多规则命中取级别最高，同级按声明顺序")
    void highestLevelWins() {
        List<EmergencyRule> rules = List.of(
                new EmergencyRule("LOW_ONE", "低", "LOW", "全科医学科", List.of("疼痛")),
                new EmergencyRule("HIGH_ONE", "高", "HIGH", "急诊科", List.of("疼痛")));
        assertThat(matcher.match("疼痛难忍", rules).orElseThrow().code()).isEqualTo("HIGH_ONE");
    }

    @Test
    @DisplayName("空关键词规则被跳过")
    void skipsEmptyKeywords() {
        List<EmergencyRule> rules = List.of(
                new EmergencyRule("EMPTY", "空", "HIGH", "急诊科", List.of()));
        assertThat(matcher.match("任何描述", rules)).isEmpty();
    }
}
