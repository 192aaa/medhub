package com.sdas.server.doctor.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sdas.server.config.TreatmentDecisionProperties;
import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.support.DecisionPayloadParser;
import com.sdas.server.doctor.support.DiagnosisSuggestion;
import com.sdas.server.doctor.support.ParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 解析器：围栏/噪声、字段校验、确定性措辞归一化。 */
class DecisionPayloadParserTest {

    private final DecisionPayloadParser parser = new DecisionPayloadParser(properties());

    @Test
    @DisplayName("标准 JSON 解析出摘要、建议与用药")
    void parsesStandardJson() {
        ParseResult result = parser.parse(payload("\"确诊为肺炎\""));

        assertThat(result.success()).isTrue();
        assertThat(result.decision().summary().chiefComplaint()).isEqualTo("咳嗽 3 天");
        assertThat(result.decision().suggestions()).hasSize(1);
        assertThat(result.decision().medications()).hasSize(1);
        assertThat(result.decision().medications().get(0).drugName()).isEqualTo("阿莫西林胶囊");
    }

    @Test
    @DisplayName("```json 围栏与前后噪声均被剥离")
    void stripsFenceAndNoise() {
        String noisy = "以下是结果：\n```json\n" + payload("\"疑似肺炎\"") + "\n```\n以上。";

        ParseResult result = parser.parse(noisy);

        assertThat(result.success()).isTrue();
        assertThat(result.decision().summary().chiefComplaint()).isEqualTo("咳嗽 3 天");
    }

    @Test
    @DisplayName("缺 summary 字段判为 PARSE_FAILED，且不回传原文")
    void missingSummaryFails() {
        ParseResult result = parser.parse("{\"suggestions\":[]}");

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).isEqualTo(DegradedReason.PARSE_FAILED);
        assertThat(result.decision()).isNull();
    }

    @Test
    @DisplayName("空白输出判为 EMPTY")
    void blankInputFails() {
        assertThat(parser.parse("  ").reason()).isEqualTo(DegradedReason.EMPTY);
    }

    @Test
    @DisplayName("非法 likelihood 降级为 LOW")
    void invalidLikelihoodFallsBack() {
        ParseResult result = parser.parse(payload("\"疑似肺炎\"").replace("MEDIUM", "CERTAIN"));

        assertThat(result.success()).isTrue();
        assertThat(result.decision().suggestions().get(0).likelihood()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("确定性措辞被去词并前置「疑似」")
    void normalizesDeterministicWording() {
        ParseResult result = parser.parse(payload("\"确诊为肺炎\""));

        DiagnosisSuggestion suggestion = result.decision().suggestions().get(0);
        assertThat(suggestion.diagnosis()).doesNotContain("确诊").startsWith("疑似");
    }

    @Test
    @DisplayName("「一定是流感」同样被归一化")
    void normalizesMustBeWording() {
        ParseResult result = parser.parse(payload("\"一定是流感\""));

        assertThat(result.decision().suggestions().get(0).diagnosis())
                .doesNotContain("一定是").startsWith("疑似");
    }

    @Test
    @DisplayName("去词后过短的条目被丢弃")
    void dropsTooShortDiagnosis() {
        ParseResult result = parser.parse(payload("\"确诊\""));

        assertThat(result.success()).isTrue();
        assertThat(result.decision().suggestions()).isEmpty();
    }

    @Test
    @DisplayName("basis 为空的建议被丢弃")
    void dropsSuggestionWithoutBasis() {
        String json = payload("\"疑似肺炎\"").replace("\"basis\": [\"咳嗽 3 天\"]",
                "\"basis\": []");

        ParseResult result = parser.parse(json);

        assertThat(result.success()).isTrue();
        assertThat(result.decision().suggestions()).isEmpty();
    }

    private static String payload(String diagnosis) {
        return "{\n"
                + "  \"summary\": {\n"
                + "    \"chiefComplaint\": \"咳嗽 3 天\",\n"
                + "    \"presentIllness\": \"受凉后咳嗽\",\n"
                + "    \"dialogHighlights\": [],\n"
                + "    \"riskAlerts\": []\n"
                + "  },\n"
                + "  \"suggestions\": [\n"
                + "    {\"diagnosis\": " + diagnosis + ", \"likelihood\": \"MEDIUM\",\n"
                + "     \"basis\": [\"咳嗽 3 天\"], \"workup\": [], \"riskAlert\": null}\n"
                + "  ],\n"
                + "  \"medications\": [\n"
                + "    {\"drugName\": \"阿莫西林胶囊\", \"dosage\": \"口服，一次 2 粒\","
                + " \"caution\": \"青霉素过敏者禁用\"}\n"
                + "  ]\n"
                + "}";
    }

    private static TreatmentDecisionProperties properties() {
        return new TreatmentDecisionProperties(null, 0, 0, 0, 0, 0, 5, 10, 0, 0);
    }
}
