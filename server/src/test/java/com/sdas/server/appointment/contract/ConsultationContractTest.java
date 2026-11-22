package com.sdas.server.appointment.contract;

import static com.sdas.server.appointment.support.AppointmentFixtures.body;
import static com.sdas.server.appointment.support.AppointmentFixtures.createAppointment;
import static com.sdas.server.appointment.support.AppointmentFixtures.createPaidAppointment;
import static com.sdas.server.appointment.support.AppointmentFixtures.get;
import static com.sdas.server.appointment.support.AppointmentFixtures.postJson;
import static com.sdas.server.appointment.support.AppointmentFixtures.verifiedPatient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdas.server.appointment.support.AppointmentFixtures;
import com.sdas.server.appointment.support.AppointmentTestSupport;
import com.sdas.server.appointment.support.AppointmentFixtures.PatientSession;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 端点 8/9 契约测试（tasks.md T045）：提交问诊单与详情查询的正向字段与异常码。
 */
class ConsultationContractTest extends AppointmentTestSupport {

    private static final String COMPLAINT = "咳嗽三天伴有轻微发热";

    @BeforeEach
    void setUp() {
        AppointmentFixtures.seedCatalog(jdbcTemplate);
        when(summaryClient.summarize(anyString(), any())).thenReturn("AI 摘要：咳嗽三天");
    }

    @Test
    void submitReturnsPendingStatusAndSummary() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);

        MvcResult result = mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\"" + COMPLAINT
                                + "\",\"description\":\"夜间加重\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = body(result).path("data");
        assertTrue(data.path("consultationId").asLong() > 0);
        assertEquals(appointmentId, data.path("appointmentId").asLong());
        assertEquals("PENDING", data.path("status").asText());
        assertEquals("AI 摘要：咳嗽三天", data.path("summary").asText());
        assertEquals(0, data.path("imageCount").asInt());
        assertEquals(AppointmentFixtures.DOCTOR_ONLINE, data.path("doctorId").asLong());
    }

    @Test
    void submitRejectsUnpaidAppointment() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE);

        mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\"" + COMPLAINT
                                + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前挂号单状态不允许该操作"));
    }

    @Test
    void submitRejectsOtherPatientAppointment() throws Exception {
        PatientSession owner = verifiedPatient(mockMvc, jdbcTemplate);
        PatientSession stranger = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, owner.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);

        mockMvc.perform(postJson("/api/v1/consultations", stranger.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\"" + COMPLAINT
                                + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("挂号单不存在或不属于当前患者"));
    }

    @Test
    void submitRejectsShortComplaint() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);

        mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\"咳嗽三天\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void submitRejectsTooManyImages() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);
        List<String> urls = IntStream.rangeClosed(1, 10)
                .mapToObj(index -> "/static/consultation-images/" + index + ".png")
                .collect(Collectors.toList());
        String imageUrls = urls.stream().map(url -> "\"" + url + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\"" + COMPLAINT
                                + "\",\"imageUrls\":" + imageUrls + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("图片数量不得超过 9 张"));
    }

    @Test
    void submitTwiceForSameAppointmentIsRejected() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);
        String requestBody = "{\"appointmentId\":" + appointmentId + ",\"complaint\":\""
                + COMPLAINT + "\"}";
        mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(), requestBody))
                .andExpect(status().isOk());

        mockMvc.perform(postJson("/api/v1/consultations", patient.accessToken(), requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前挂号单状态不允许该操作"));
    }

    @Test
    void submitRequiresAuthentication() throws Exception {
        mockMvc.perform(postJson("/api/v1/consultations", null,
                        "{\"appointmentId\":1,\"complaint\":\"" + COMPLAINT + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void detailReturnsLinkedImages() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createPaidAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE,
                AppointmentFixtures.ONLINE_FEE);
        String imageUrl = "/static/consultation-images/detail-" + appointmentId + ".png";
        jdbcTemplate.update("INSERT INTO consultation_image (image_url, content_type, size_bytes) "
                + "VALUES (?, 'image/png', 128)", imageUrl);

        MvcResult submitted = mockMvc.perform(postJson("/api/v1/consultations",
                        patient.accessToken(),
                        "{\"appointmentId\":" + appointmentId + ",\"complaint\":\"" + COMPLAINT
                                + "\",\"imageUrls\":[\"" + imageUrl + "\"]}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode submittedData = body(submitted).path("data");
        assertEquals(1, submittedData.path("imageCount").asInt());
        long consultationId = submittedData.path("consultationId").asLong();

        MvcResult detail = mockMvc.perform(get("/api/v1/consultations/" + consultationId,
                        patient.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = body(detail).path("data");
        assertEquals(consultationId, data.path("consultationId").asLong());
        assertEquals(COMPLAINT, data.path("complaint").asText());
        assertEquals("PENDING", data.path("status").asText());
        assertEquals(1, data.path("images").size());
        assertEquals(imageUrl, data.path("images").get(0).path("url").asText());
    }

    @Test
    void detailOtherPatientConsultationIsForbidden() throws Exception {
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

        mockMvc.perform(get("/api/v1/consultations/" + consultationId, stranger.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("禁止访问他人问诊资料"));
    }

    @Test
    void detailUnknownConsultationReturns404() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(get("/api/v1/consultations/999999", patient.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("问诊单不存在或不属于当前患者"));
    }

    @Test
    void detailRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/consultations/1", null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
