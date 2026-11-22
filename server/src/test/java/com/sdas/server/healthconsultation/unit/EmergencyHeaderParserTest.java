package com.sdas.server.healthconsultation.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sdas.server.healthconsultation.support.EmergencyHeaderParser;
import com.sdas.server.healthconsultation.support.EmergencyHeaderParser.ModelEmergencySignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 首行协议解析。 */
class EmergencyHeaderParserTest {

    @Test
    @DisplayName("YES/NO 解析并从正文剥离首行")
    void parsesHeaderAndStrips() {
        EmergencyHeaderParser.ParsedAnswer yes =
                EmergencyHeaderParser.parse("EMERGENCY: YES\n请立即就医。");
        assertThat(yes.signal()).isEqualTo(ModelEmergencySignal.YES);
        assertThat(yes.body()).isEqualTo("请立即就医。");

        EmergencyHeaderParser.ParsedAnswer no =
                EmergencyHeaderParser.parse("emergency:no\n多饮水休息。");
        assertThat(no.signal()).isEqualTo(ModelEmergencySignal.NO);
        assertThat(no.body()).isEqualTo("多饮水休息。");
    }

    @Test
    @DisplayName("全角冒号与多余空格容错")
    void toleratesFullWidthColon() {
        EmergencyHeaderParser.ParsedAnswer parsed =
                EmergencyHeaderParser.parse("EMERGENCY ： YES\r\n建议复诊。");
        assertThat(parsed.signal()).isEqualTo(ModelEmergencySignal.YES);
        assertThat(parsed.body()).isEqualTo("建议复诊。");
    }

    @Test
    @DisplayName("首行缺失按 UNAVAILABLE 处理并保留原文")
    void missingHeaderIsUnavailable() {
        EmergencyHeaderParser.ParsedAnswer parsed =
                EmergencyHeaderParser.parse("建议多喝水并观察体温变化。");
        assertThat(parsed.signal()).isEqualTo(ModelEmergencySignal.UNAVAILABLE);
        assertThat(parsed.body()).isEqualTo("建议多喝水并观察体温变化。");
    }

    @Test
    @DisplayName("空内容按 UNAVAILABLE 处理")
    void emptyIsUnavailable() {
        EmergencyHeaderParser.ParsedAnswer parsed = EmergencyHeaderParser.parse("");
        assertThat(parsed.signal()).isEqualTo(ModelEmergencySignal.UNAVAILABLE);
        assertThat(parsed.body()).isEmpty();
    }
}
