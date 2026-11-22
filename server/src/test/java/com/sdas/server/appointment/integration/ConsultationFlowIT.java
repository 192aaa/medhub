package com.sdas.server.appointment.integration;

import static com.sdas.server.appointment.support.AppointmentFixtures.auditCount;
import static com.sdas.server.appointment.support.AppointmentFixtures.body;
import static com.sdas.server.appointment.support.AppointmentFixtures.createPaidAppointment;
import static com.sdas.server.appointment.support.AppointmentFixtures.get;
import static com.sdas.server.appointment.support.AppointmentFixtures.postJson;
import static com.sdas.server.appointment.support.AppointmentFixtures.verifiedPatient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdas.server.appointment.support.AppointmentFixtures;
import com.sdas.server.appointment.support.AppointmentFixtures.PatientSession;
import com.sdas.server.appointment.support.AppointmentTestSupport;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 图文问诊单集成测试（tasks.md T046）：状态落库、主诉边界、幂等、越权与摘要降级。
 */
class ConsultationFlowIT extends AppointmentTestSupport {

    private static final String COMPLAINT = "咳嗽三天伴有轻微发热";

    @BeforeEach
    void setUp() {
        AppointmentFixtures.seedCatalog(jdbcTemplate);
        when(summaryClient.summarize(anyString(), any())).thenReturn("AI 摘要");
    }

    @Test
    void submitPersistsPendingWithAudit() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);

        MvcResult result = mockMvc.perform(postJson("/api/v1/consultations",
                        patient.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\"" + COMPLAINT
                                + "\",\"description\":\"夜间加重\"}"))
                .andExpect(status().isOk()).andReturn();
        long consultationId = body(result).path("data").path("consultationId").asLong();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, timeout_status, complaint, summary, patient_id FROM consultation "
                        + "WHERE id = ?", consultationId);
        assertEquals("PENDING", row.get("status"));
        assertEquals("NONE", row.get("timeout_status"));
        assertEquals(COMPLAINT, row.get("complaint"));
        assertEquals("AI 摘要", row.get("summary"));
        assertEquals(patient.patientId(), ((Number) row.get("patient_id")).longValue());
        assertEquals(1, auditCount(jdbcTemplate, "CONSULTATION_SUBMITTED", patient.patientId()));
    }

    @Test
    void complaintLengthBoundariesAreEnforced() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);

        mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\""
                                + "咳".repeat(9) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\""
                                + "咳".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());

        PatientSession secondPatient = verifiedPatient(mockMvc, jdbcTemplate);
        long secondAppointment = createPaidAppointment(mockMvc, secondPatient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);
        mockMvc.perform(postJson("/api/v1/consultations", secondPatient.accessToken(),
                        "{\"appointmentId\":" + secondAppointment + ",\"complaint\":\""
                                + "咳".repeat(10) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.status").value("PENDING"));

        PatientSession thirdPatient = verifiedPatient(mockMvc, jdbcTemplate);
        long thirdAppointment = createPaidAppointment(mockMvc, thirdPatient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);
        mockMvc.perform(postJson("/api/v1/consultations", thirdPatient.accessToken(),
                        "{\"appointmentId\":" + thirdAppointment + ",\"complaint\":\""
                                + "咳".repeat(200) + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void summaryFailureDoesNotBlockSubmission() throws Exception {
        when(summaryClient.summarize(anyString(), any())).thenReturn(null);
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);

        MvcResult result = mockMvc.perform(postJson("/api/v1/consultations",
                        patient.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\"" + COMPLAINT
                                + "\"}"))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = body(result).path("data");
        assertEquals("PENDING", data.path("status").asText());
        assertTrue(data.path("summary").isNull());
    }

    @Test
    void duplicateSubmitKeepsSingleConsultation() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);
        String requestBody = "{\"appointmentId\":" + appointmentId + ",\"complaint\":\""
                + COMPLAINT + "\"}";

        mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(), requestBody))
                .andExpect(status().isOk());
        mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(), requestBody))
                .andExpect(status().isBadRequest());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM consultation WHERE appointment_id = ?", Integer.class,
                appointmentId);
        assertEquals(1, count == null ? 0 : count.intValue());
        assertEquals(1, auditCount(jdbcTemplate, "CONSULTATION_SUBMITTED", patient.patientId()));
    }

    @Test
    void consultationDetailIsScopedToOwner() throws Exception {
        PatientSession owner = verifiedPatient(mockMvc, jdbcTemplate);
        PatientSession stranger = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, owner.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);
        MvcResult submitted = mockMvc.perform(postJson("/api/v1/consultations",
                        owner.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\"" + COMPLAINT
                                + "\"}"))
                .andExpect(status().isOk()).andReturn();
        long consultationId = body(submitted).path("data").path("consultationId").asLong();

        mockMvc.perform(get("/api/v1/consultations/" + consultationId, owner.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/consultations/" + consultationId, stranger.accessToken()))
                .andExpect(status().isForbidden());
    }
}
