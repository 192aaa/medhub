package com.sdas.server.healthconsultation.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sdas.server.healthconsultation.model.EmergencyVerdict;
import com.sdas.server.healthconsultation.support.EmergencyDecisionResolver;
import com.sdas.server.healthconsultation.support.EmergencyHeaderParser.ModelEmergencySignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** M5 判定矩阵。 */
class EmergencyDecisionResolverTest {

    private final EmergencyDecisionResolver resolver = new EmergencyDecisionResolver();

    @Test
    @DisplayName("规则命中 + 模型 YES → CONFIRMED 且预警")
    void confirmed() {
        var result = resolver.resolve("CHEST_PAIN_DYSPNEA", ModelEmergencySignal.YES).orElseThrow();
        assertThat(result.verdict()).isEqualTo(EmergencyVerdict.CONFIRMED);
        assertThat(result.warned()).isTrue();
        assertThat(result.ruleCode()).isEqualTo("CHEST_PAIN_DYSPNEA");
    }

    @Test
    @DisplayName("规则命中 + 模型 NO → REJECTED 不预警但留痕")
    void rejected() {
        var result = resolver.resolve("STROKE_SIGNS", ModelEmergencySignal.NO).orElseThrow();
        assertThat(result.verdict()).isEqualTo(EmergencyVerdict.REJECTED);
        assertThat(result.warned()).isFalse();
    }

    @Test
    @DisplayName("规则命中 + 模型不可用 → UNAVAILABLE 且仍预警（安全优先）")
    void unavailableStillWarns() {
        var result = resolver.resolve("SEVERE_BLEEDING", ModelEmergencySignal.UNAVAILABLE)
                .orElseThrow();
        assertThat(result.verdict()).isEqualTo(EmergencyVerdict.UNAVAILABLE);
        assertThat(result.warned()).isTrue();
    }

    @Test
    @DisplayName("规则未命中 + 模型 YES → MODEL_ONLY 预警")
    void modelOnly() {
        var result = resolver.resolve(null, ModelEmergencySignal.YES).orElseThrow();
        assertThat(result.verdict()).isEqualTo(EmergencyVerdict.MODEL_ONLY);
        assertThat(result.warned()).isTrue();
        assertThat(result.ruleCode()).isEqualTo(EmergencyDecisionResolver.MODEL_ONLY_RULE_CODE);
    }

    @Test
    @DisplayName("规则未命中 + 模型 NO/UNAVAILABLE → 不产生留痕")
    void noRecordWhenNeither() {
        assertThat(resolver.resolve(null, ModelEmergencySignal.NO)).isEmpty();
        assertThat(resolver.resolve(null, ModelEmergencySignal.UNAVAILABLE)).isEmpty();
    }
}
