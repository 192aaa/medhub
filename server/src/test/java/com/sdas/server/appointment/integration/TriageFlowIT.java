package com.sdas.server.appointment.integration;

import static com.sdas.server.appointment.support.AppointmentFixtures.auditCount;
import static com.sdas.server.appointment.support.AppointmentFixtures.body;
import static com.sdas.server.appointment.support.AppointmentFixtures.postJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdas.server.appointment.service.TriageAiResult;
import com.sdas.server.appointment.support.AppointmentFixtures;
import com.sdas.server.appointment.support.AppointmentFixtures.PatientSession;
import com.sdas.server.appointment.support.AppointmentTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AI 导诊集成测试（tasks.md T060）：超时降级审计、会话落库、知识库命中与无命中的兜底。
 */
class TriageFlowIT extends AppointmentTestSupport {

    private static final String PATH = "/api/v1/triage-sessions";

    @BeforeEach
    void setUp() {
        AppointmentFixtures.seedCatalog(jdbcTemplate);
    }

    @Test
    void timeoutFallbackIsAuditedAndPersisted() throws Exception {
        when(triageClient.recommend(anyString())).thenReturn(TriageAiResult.timeout());
        PatientSession patient = AppointmentFixtures.newPatient(mockMvc, jdbcTemplate);

        MvcResult result = mockMvc.perform(postJson(PATH, patient.accessToken(),
                        "{\"complaint\":\"持续乏力两周\"}"))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = body(result).path("data");
        assertEquals("TIMEOUT_FALLBACK", data.path("status").asText());
        long sessionId = data.path("triageSessionId").asLong();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, kb_version, complaint, elapsed_ms FROM ai_triage_session "
                        + "WHERE id = ?", sessionId);
        assertEquals("TIMEOUT_FALLBACK", row.get("status"));
        assertEquals("PRESET", row.get("kb_version"));
        assertEquals("持续乏力两周", row.get("complaint"));
        assertTrue(((Number) row.get("elapsed_ms")).longValue() >= 0);
        assertTrue(auditCount(jdbcTemplate, "TRIAGE_TIMEOUT_FALLBACK", patient.patientId()) >= 1);
    }

    @Test
    void successStoresRecommendedDepartmentIds() throws Exception {
        when(triageClient.recommend(anyString()))
                .thenReturn(new TriageAiResult(true, List.of("内科")));
        PatientSession patient = AppointmentFixtures.newPatient(mockMvc, jdbcTemplate);

        MvcResult result = mockMvc.perform(postJson(PATH, patient.accessToken(),
                        "{\"complaint\":\"咳嗽伴发热\"}"))
                .andExpect(status().isOk()).andReturn();
        long sessionId = body(result).path("data").path("triageSessionId").asLong();

        String ids = jdbcTemplate.queryForObject(
                "SELECT recommended_department_ids FROM ai_triage_session WHERE id = ?",
                String.class, sessionId);
        assertTrue(ids != null && ids.contains(String.valueOf(AppointmentFixtures.DEPT_INTERNAL)),
                "会话须持久化推荐科室 ID：" + ids);
        assertTrue(auditCount(jdbcTemplate, "TRIAGE_RECOMMENDED", patient.patientId()) >= 1);
    }

    @Test
    void knowledgeBaseKeywordHitReturnsDepartment() throws Exception {
        when(triageClient.recommend(anyString()))
                .thenReturn(new TriageAiResult(true, List.of("呼吸科")));

        MvcResult result = mockMvc.perform(postJson(PATH, null,
                        "{\"complaint\":\"咳嗽三天\"}"))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = body(result).path("data");
        assertEquals("SUCCESS", data.path("status").asText());
        boolean matched = false;
        for (JsonNode id : data.path("recommendedDepartmentIds")) {
            if (id.asLong() == AppointmentFixtures.DEPT_INTERNAL) {
                matched = true;
            }
        }
        assertTrue(matched, "知识库关键词命中须返回内科");
    }

    @Test
    void unmatchedRecommendationFallsBackToAllDepartments() throws Exception {
        when(triageClient.recommend(anyString()))
                .thenReturn(new TriageAiResult(true, List.of("皮肤美容科")));

        MvcResult result = mockMvc.perform(postJson(PATH, null,
                        "{\"complaint\":\"失眠多梦\"}"))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = body(result).path("data");
        assertEquals("TIMEOUT_FALLBACK", data.path("status").asText());
        assertTrue(data.path("fallbackDepartments").size() >= 2);
    }
}
