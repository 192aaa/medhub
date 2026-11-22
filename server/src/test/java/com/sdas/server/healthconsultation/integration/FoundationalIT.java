package com.sdas.server.healthconsultation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sdas.server.config.HealthEmergencyProperties;
import com.sdas.server.config.HealthProperties;
import com.sdas.server.config.HospitalProperties;
import com.sdas.server.identity.support.IdentityTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 地基自检：表、知识预置、配置绑定与默认规则（tasks.md T033）。 */
class FoundationalIT extends IdentityTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HealthProperties healthProperties;

    @Autowired
    private HealthEmergencyProperties emergencyProperties;

    @Autowired
    private HospitalProperties hospitalProperties;

    @Test
    @DisplayName("V9 建三张表")
    void tablesExist() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('ai_health_session', 'ai_health_message', "
                        + "'ai_health_emergency_decision')", Integer.class);
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("V10 预置 58 条知识并创建兜底科室")
    void knowledgePreset() {
        Integer preset = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM department_disease_kb WHERE source = 'PRESET' "
                        + "AND kb_version = '2026.11'", Integer.class);
        assertThat(preset).isEqualTo(58);

        Integer department = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM department WHERE name = '全科医学科'", Integer.class);
        assertThat(department).isEqualTo(1);
    }

    @Test
    @DisplayName("配置绑定与默认规则回落")
    void configurationBound() {
        assertThat(healthProperties.model()).isEqualTo("DeepSeek-Flash-1017");
        assertThat(healthProperties.historyWindow()).isEqualTo(12);
        assertThat(healthProperties.messageMaxChars()).isEqualTo(2000);
        assertThat(hospitalProperties.emergencyPhone()).isNotBlank();
        assertThat(emergencyProperties.effectiveRules()).hasSize(3);
    }
}
