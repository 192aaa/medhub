package com.sdas.server.doctor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.TreatmentDecisionProperties;
import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.model.TreatmentDecisionStatus;
import com.sdas.server.doctor.model.TreatmentTriggerType;
import com.sdas.server.doctor.model.converter.DegradedReasonConverter;
import com.sdas.server.doctor.model.converter.TreatmentDecisionStatusConverter;
import com.sdas.server.doctor.model.converter.TreatmentTriggerTypeConverter;
import com.sdas.server.doctor.repository.AiDecisionSessionRepository;
import com.sdas.server.doctor.support.DoctorTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 006 地基验收（T025）：V11 六列与索引、既有行可读、枚举默认值、配置绑定、错误码文案。 */
class TreatmentDecisionFoundationalIT extends DoctorTestSupport {

    private static final List<String> NEW_COLUMNS = List.of("trigger_type", "dialog_version",
            "result_json", "degraded_reason", "applied_item_count", "adopted_at");

    @Autowired
    private TreatmentDecisionProperties properties;

    @Autowired
    private AiDecisionSessionRepository sessionRepository;

    @Test
    @DisplayName("V11 六列与幂等索引存在")
    void migrationApplied() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_decision_session'",
                String.class);

        assertThat(columns).containsAll(NEW_COLUMNS);

        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_decision_session' "
                        + "AND INDEX_NAME = 'idx_ai_dialog_version'", Integer.class);
        assertThat(indexCount).isNotNull().isPositive();
    }

    @Test
    @DisplayName("扩展后仓储查询可用（既有行仍可读）")
    void repositoryStillReadable() {
        assertThat(sessionRepository.findLatestByConsultationIdAndDoctorId(999_999L, 999_999L))
                .isEmpty();
    }

    @Test
    @DisplayName("三个枚举未知值回落到安全默认值")
    void enumConvertersDefaultValues() {
        assertThat(new TreatmentDecisionStatusConverter().defaultValue())
                .isEqualTo(TreatmentDecisionStatus.EMPTY_FALLBACK);
        assertThat(new TreatmentTriggerTypeConverter().defaultValue())
                .isEqualTo(TreatmentTriggerType.AUTO);
        assertThat(new DegradedReasonConverter().defaultValue()).isEqualTo(DegradedReason.NONE);
        assertThat(new TreatmentDecisionStatusConverter()
                .convertToEntityAttribute("NOT_A_STATUS"))
                .isEqualTo(TreatmentDecisionStatus.EMPTY_FALLBACK);
    }

    @Test
    @DisplayName("10 个配置项绑定成功且默认值与 data-model 一致")
    void propertiesBound() {
        assertThat(properties.model()).isEqualTo("DeepSeek-Flash-1017");
        assertThat(properties.timeoutMillis()).isEqualTo(15000L);
        assertThat(properties.maxOutputTokens()).isEqualTo(2048);
        assertThat(properties.dialogMaxMessages()).isEqualTo(40);
        assertThat(properties.dialogMaxChars()).isEqualTo(3000);
        assertThat(properties.knowledgeTopK()).isEqualTo(5);
        assertThat(properties.maxSuggestions()).isEqualTo(5);
        assertThat(properties.maxMedications()).isEqualTo(10);
        assertThat(properties.regenerateLimitPerHour()).isEqualTo(3);
        assertThat(properties.reuseWindowMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("4 个新增错误码 code 与 message 逐字一致")
    void errorCodesMatchContract() {
        assertThat(ErrorCode.TREATMENT_DECISION_NOT_FOUND.getCode()).isEqualTo(404);
        assertThat(ErrorCode.TREATMENT_DECISION_NOT_FOUND.getMessage())
                .isEqualTo("暂无治疗决策辅助记录");
        assertThat(ErrorCode.TREATMENT_DECISION_BUSY.getCode()).isEqualTo(409);
        assertThat(ErrorCode.TREATMENT_DECISION_BUSY.getMessage()).isEqualTo("辅助生成进行中，请稍候");
        assertThat(ErrorCode.TREATMENT_DECISION_RATE_LIMITED.getCode()).isEqualTo(429);
        assertThat(ErrorCode.TREATMENT_DECISION_RATE_LIMITED.getMessage())
                .isEqualTo("辅助生成过于频繁，请稍后重试");
        assertThat(ErrorCode.INVALID_TREATMENT_ADOPTION.getCode()).isEqualTo(400);
        assertThat(ErrorCode.INVALID_TREATMENT_ADOPTION.getMessage()).isEqualTo("采纳数量不合法");
    }
}
