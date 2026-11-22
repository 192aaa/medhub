package com.sdas.server.healthconsultation.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sdas.server.healthconsultation.support.HealthDisclaimer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 免责声明唯一来源（SC-002）。 */
class HealthDisclaimerTest {

    @Test
    @DisplayName("文案逐字与契约一致")
    void textMatchesContract() {
        assertThat(HealthDisclaimer.TEXT).isEqualTo("仅供参考，不构成医疗诊断");
    }
}
