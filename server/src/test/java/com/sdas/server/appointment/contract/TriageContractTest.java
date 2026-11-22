package com.sdas.server.appointment.contract;

import static com.sdas.server.appointment.support.AppointmentFixtures.body;
import static com.sdas.server.appointment.support.AppointmentFixtures.postJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdas.server.appointment.service.TriageAiResult;
import com.sdas.server.appointment.support.AppointmentFixtures;
import com.sdas.server.appointment.support.AppointmentTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 端点 4 契约测试（tasks.md T059）：推荐成功、超时降级、知识库兜底、匿名访问与 CSRF。
 */
class TriageContractTest extends AppointmentTestSupport {

    private static final String PATH = "/api/v1/triage-sessions";

    @BeforeEach
    void setUp() {
        AppointmentFixtures.seedCatalog(jdbcTemplate);
    }

    @Test
    void recommendReturnsMatchedDepartments() throws Exception {
        when(triageClient.recommend(anyString()))
                .thenReturn(new TriageAiResult(true, List.of("内科")));

        MvcResult result = mockMvc.perform(postJson(PATH, null,
                        "{\"complaint\":\"持续咳嗽伴发热\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = body(result).path("data");
        assertEquals("SUCCESS", data.path("status").asText());
        assertTrue(containsDepartment(data.path("recommendedDepartmentIds"),
                AppointmentFixtures.DEPT_INTERNAL), "推荐结果须包含内科");
        assertTrue(containsDepartmentName(data.path("recommendedDepartments"), "内科"));
        assertEquals(0, data.path("fallbackDepartments").size());
        assertTrue(data.path("modelUsed").asText().length() > 0);
        assertTrue(data.path("elapsedMs").asLong() >= 0);
        assertTrue(data.path("triageSessionId").asLong() > 0);
    }

    @Test
    void timeoutFallsBackToFullDepartmentList() throws Exception {
        when(triageClient.recommend(anyString())).thenReturn(TriageAiResult.timeout());

        MvcResult result = mockMvc.perform(postJson(PATH, null,
                        "{\"complaint\":\"不明原因乏力\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = body(result).path("data");
        assertEquals("TIMEOUT_FALLBACK", data.path("status").asText());
        assertEquals(0, data.path("recommendedDepartments").size());
        assertTrue(data.path("fallbackDepartments").size() >= 2, "降级须返回全量科室列表");
        boolean internalPresent = false;
        for (JsonNode department : data.path("fallbackDepartments")) {
            if (department.path("departmentId").asLong() == AppointmentFixtures.DEPT_INTERNAL) {
                internalPresent = true;
            }
        }
        assertTrue(internalPresent);
    }

    @Test
    void unknownDepartmentNameFallsBackToKnowledgeBase() throws Exception {
        when(triageClient.recommend(anyString()))
                .thenReturn(new TriageAiResult(true, List.of("呼吸科")));

        MvcResult result = mockMvc.perform(postJson(PATH, null,
                        "{\"complaint\":\"咳嗽三天\"}"))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = body(result).path("data");
        assertEquals("SUCCESS", data.path("status").asText());
        assertTrue(containsDepartment(data.path("recommendedDepartmentIds"),
                AppointmentFixtures.DEPT_INTERNAL), "知识库兜底须命中内科");
    }

    private boolean containsDepartment(JsonNode ids, long departmentId) {
        for (JsonNode id : ids) {
            if (id.asLong() == departmentId) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDepartmentName(JsonNode departments, String name) {
        for (JsonNode department : departments) {
            if (name.equals(department.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void blankComplaintReturns400() throws Exception {
        mockMvc.perform(postJson(PATH, null, "{\"complaint\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void missingCsrfIsRejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(PATH)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"complaint\":\"持续咳嗽伴发热\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void authenticatedPatientTriageSucceeds() throws Exception {
        when(triageClient.recommend(anyString()))
                .thenReturn(new TriageAiResult(true, List.of("外科")));
        AppointmentFixtures.PatientSession patient =
                AppointmentFixtures.newPatient(mockMvc, jdbcTemplate);

        MvcResult result = mockMvc.perform(postJson(PATH, patient.accessToken(),
                        "{\"complaint\":\"膝盖外伤后疼痛\"}"))
                .andExpect(status().isOk()).andReturn();

        assertEquals("SUCCESS", body(result).path("data").path("status").asText());
    }
}
