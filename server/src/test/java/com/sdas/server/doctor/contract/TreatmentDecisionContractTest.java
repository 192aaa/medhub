package com.sdas.server.doctor.contract;

import static com.sdas.server.doctor.contract.DoctorContractFixtures.seedDoctor;
import static com.sdas.server.doctor.contract.DoctorContractFixtures.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.service.TreatmentDecisionResult;
import com.sdas.server.doctor.support.DoctorTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 端点 1–4 契约测试（T028）：正向结构、降级结构、状态/权限/CSRF 异常表。
 *
 * <p>越权与不存在的响应体不得包含主诉、描述、对话等任何病情字段（G4）。</p>
 */
class TreatmentDecisionContractTest extends DoctorTestSupport {

    private static final String PATH = "/api/v1/workbench/consultations/" + 501
            + "/treatment-decision";

    private static final String DISCLAIMER = "AI 生成，仅供参考，最终诊疗决策由医生负责";

    private static final String SECOND_DOCTOR_USERNAME = "doctor_li";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        redisTemplate.delete(List.of("ai:treatment:lock:501", "ai:treatment:rate:501"));
        jdbcTemplate.update("DELETE FROM ai_decision_session WHERE consultation_id = ?", 501L);
        seedDoctor(jdbcTemplate);
        seedConsultationInProgress(501L, 1001L, 101L);
        seedConsultationStatus(501L, "IN_PROGRESS");
        seedDrugs();
        seedDialog(501L, "咳嗽三天，夜间加重", "无发热");
        token = DoctorContractFixtures.doctorLogin(mockMvc);
    }

    @Test
    @DisplayName("端点 1 成功：结构化字段齐备")
    void generateSuccessStructure() throws Exception {
        when(treatmentDecisionClient.decide(any()))
                .thenReturn(TreatmentDecisionResult.success(successJson(), "test-model"));

        mockMvc.perform(post(PATH, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.consultationId").value(501))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.triggerType").value("AUTO"))
                .andExpect(jsonPath("$.data.reused").value(false))
                .andExpect(jsonPath("$.data.summary.chiefComplaint").value("咳嗽伴咳痰 3 天"))
                .andExpect(jsonPath("$.data.summary.evidenceLimited").value(false))
                .andExpect(jsonPath("$.data.suggestions[0].diagnosis").value("疑似上呼吸道感染"))
                .andExpect(jsonPath("$.data.suggestions[0].likelihood").value("MEDIUM"))
                .andExpect(jsonPath("$.data.medicationDrafts[0].drugId").value(901))
                .andExpect(jsonPath("$.data.medicationDrafts[0].spec").value("0.25g*20"))
                .andExpect(jsonPath("$.data.ignoredMedications").isEmpty())
                .andExpect(jsonPath("$.data.disclaimer").value(DISCLAIMER))
                .andExpect(jsonPath("$.data.degradedReason").value("NONE"))
                .andExpect(jsonPath("$.data.generatedAt").isString());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_decision_session WHERE consultation_id = 501",
                Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("端点 1 降级：200 + 空结构 + 降级原因且仍留痕")
    void generateDegradesWithout5xx() throws Exception {
        when(treatmentDecisionClient.decide(any()))
                .thenReturn(TreatmentDecisionResult.degraded(DegradedReason.TIMEOUT));

        mockMvc.perform(post(PATH, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("TIMEOUT_FALLBACK"))
                .andExpect(jsonPath("$.data.summary").value(nullValue()))
                .andExpect(jsonPath("$.data.suggestions").isEmpty())
                .andExpect(jsonPath("$.data.medicationDrafts").isEmpty())
                .andExpect(jsonPath("$.data.disclaimer").value(DISCLAIMER))
                .andExpect(jsonPath("$.data.degradedReason").value("TIMEOUT"));

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM ai_decision_session "
                + "WHERE consultation_id = 501", String.class);
        assertThat(dbStatus).isEqualTo("TIMEOUT_FALLBACK");
    }

    @Test
    @DisplayName("缺 CSRF → 403 请求校验失败")
    void missingCsrfRejected() throws Exception {
        mockMvc.perform(post(PATH, false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("请求校验失败"));
    }

    @Test
    @DisplayName("非医生角色 → 403 仅医生可访问")
    void roleRequired() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("仅医生可访问该功能"));
    }

    @Test
    @DisplayName("非接诊医生 → 403 且响应体不含病情字段")
    void forbiddenBodyHasNoClinicalData() throws Exception {
        seedSecondDoctor();
        String otherToken = login(SECOND_DOCTOR_USERNAME);

        MvcResult result = mockMvc.perform(get(PATH, otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("禁止访问他人问诊资料"))
                .andReturn();

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("咳嗽伴咳痰三天").doesNotContain("夜间加重");
    }

    @Test
    @DisplayName("问诊单不存在 → 404")
    void consultationNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/workbench/consultations/999999/treatment-decision", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("问诊单不存在或不属于当前患者"));
    }

    @Test
    @DisplayName("问诊单状态非 IN_PROGRESS → 400")
    void invalidStatus() throws Exception {
        seedConsultationStatus(501L, "PENDING");

        mockMvc.perform(post(PATH, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("当前问诊单状态不允许该操作"));
    }

    @Test
    @DisplayName("端点 2 无记录 → 404 暂无记录")
    void latestWithoutRecord() throws Exception {
        mockMvc.perform(get(PATH, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("暂无治疗决策辅助记录"));
    }

    @Test
    @DisplayName("端点 4 采纳数量越界 → 400")
    void invalidAdoption() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post(PATH + "/adoptions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content("{\"appliedItemCount\":21}");
        withCsrf(builder);

        mockMvc.perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("采纳数量不合法"));
    }

    @Test
    @DisplayName("并发生成未获锁 → 409")
    void busyLock() throws Exception {
        redisTemplate.opsForValue().set("ai:treatment:lock:501", "other-request",
                java.time.Duration.ofSeconds(30));

        mockMvc.perform(post(PATH, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("辅助生成进行中，请稍候"));
    }

    @Test
    @DisplayName("重新生成超频 → 429")
    void rateLimited() throws Exception {
        redisTemplate.opsForValue().set("ai:treatment:rate:501", "3",
                java.time.Duration.ofSeconds(3600));

        mockMvc.perform(post(PATH + "/regenerate", true))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("辅助生成过于频繁，请稍后重试"));
    }

    private MockHttpServletRequestBuilder post(String path, boolean csrf) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token);
        if (csrf) {
            withCsrf(builder);
        }
        return builder;
    }

    private MockHttpServletRequestBuilder get(String path, String accessToken) {
        return MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + accessToken);
    }

    private String login(String username) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post("/api/v1/workbench/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"password\"}");
        withCsrf(builder);
        MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString()).path("data")
                .path("accessToken").asText();
    }

    private void seedSecondDoctor() {
        String hash = "$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm";
        jdbcTemplate.update("INSERT IGNORE INTO doctor (id, department_id, name, title, hospital, "
                + "online_status) VALUES (102, 1, '李*', '主治医师', '示例医院', 'ONLINE')");
        jdbcTemplate.update("INSERT IGNORE INTO doctor_account (username, password_hash, doctor_id, "
                + "name_mask, status) VALUES (?, ?, 102, '李*', 'ACTIVE')",
                SECOND_DOCTOR_USERNAME, hash);
    }

    private static String successJson() {
        return "{\"summary\":{\"chiefComplaint\":\"咳嗽伴咳痰 3 天\","
                + "\"presentIllness\":\"3 天前受凉后咳嗽\","
                + "\"dialogHighlights\":[\"夜间咳嗽加重\"],\"riskAlerts\":[]},"
                + "\"suggestions\":[{\"diagnosis\":\"疑似上呼吸道感染\","
                + "\"likelihood\":\"MEDIUM\",\"basis\":[\"咳嗽咳痰 3 天\"],"
                + "\"workup\":[\"必要时血常规\"],\"riskAlert\":null}],"
                + "\"medications\":[{\"drugName\":\"阿莫西林胶囊\","
                + "\"dosage\":\"口服，一次 2 粒\",\"caution\":\"青霉素过敏者禁用\"}]}";
    }
}
