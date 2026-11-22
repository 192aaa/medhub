package com.sdas.server.appointment.contract;

import static com.sdas.server.appointment.support.AppointmentFixtures.body;
import static com.sdas.server.appointment.support.AppointmentFixtures.createAppointment;
import static com.sdas.server.appointment.support.AppointmentFixtures.get;
import static com.sdas.server.appointment.support.AppointmentFixtures.postJson;
import static com.sdas.server.appointment.support.AppointmentFixtures.verifiedPatient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdas.server.appointment.support.AppointmentFixtures;
import com.sdas.server.appointment.support.AppointmentTestSupport;
import com.sdas.server.appointment.support.AppointmentFixtures.PatientSession;
import com.sdas.server.config.AppointmentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 端点 5/6a/6b 契约测试（tasks.md T027）：创建预约、支付、支付状态查询的正向与异常码。
 */
class AppointmentPaymentContractTest extends AppointmentTestSupport {

    @MockitoBean
    private AppointmentProperties appointmentProperties;

    @BeforeEach
    void setUp() {
        AppointmentFixtures.seedCatalog(jdbcTemplate);
        when(appointmentProperties.mockPaymentEnabled()).thenReturn(true);
    }

    @Test
    void createReturnsFeeSnapshotAndPendingStatus() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        MvcResult result = mockMvc.perform(postJson("/api/v1/appointments", patient.accessToken(),
                        "{\"doctorId\":1101,\"scheduleId\":1201,\"patientNote\":\"头痛\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = body(result).path("data");
        assertTrue(data.path("appointmentId").asLong() > 0);
        assertEquals(30.00, data.path("feeAmount").asDouble(), 0.001);
        assertEquals("UNPAID", data.path("paymentStatus").asText());
        assertEquals("PENDING_PAYMENT", data.path("appointmentStatus").asText());
        assertEquals(AppointmentFixtures.DEPT_INTERNAL, data.path("departmentId").asLong());
        assertEquals("MORNING", data.path("period").asText());
    }

    @Test
    void createRejectsUnverifiedPatient() throws Exception {
        PatientSession patient = AppointmentFixtures.newPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(postJson("/api/v1/appointments", patient.accessToken(),
                        "{\"doctorId\":1101,\"scheduleId\":1201}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("请先完成实名认证或确认复诊资格"));
    }

    @Test
    void createRejectsOfflineDoctor() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(postJson("/api/v1/appointments", patient.accessToken(),
                        "{\"doctorId\":1104,\"scheduleId\":1203}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("所选医生或时段不可预约"));
    }

    @Test
    void createRejectsFullSchedule() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(postJson("/api/v1/appointments", patient.accessToken(),
                        "{\"doctorId\":1101,\"scheduleId\":1202}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("所选医生或时段不可预约"));
    }

    @Test
    void createRejectsUnknownDoctor() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(postJson("/api/v1/appointments", patient.accessToken(),
                        "{\"doctorId\":999999,\"scheduleId\":1201}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("医生不存在"));
    }

    @Test
    void createRejectsDuplicatePatientSchedule() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        createAppointment(mockMvc, patient.accessToken(), AppointmentFixtures.DOCTOR_ONLINE,
                AppointmentFixtures.SCHEDULE_BOOKABLE);

        mockMvc.perform(postJson("/api/v1/appointments", patient.accessToken(),
                        "{\"doctorId\":1101,\"scheduleId\":1201}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前挂号单状态不允许该操作"));
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(postJson("/api/v1/appointments", null,
                        "{\"doctorId\":1101,\"scheduleId\":1201}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未授权"));
    }

    @Test
    void createWithoutCsrfIsRejectedBeforeBusinessLogic() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + patient.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":1101,\"scheduleId\":1201}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void paySucceedsAndUpdatesBothStatuses() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE);

        MvcResult result = mockMvc.perform(postJson(
                        "/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken(), "{\"channel\":\"MOCK\",\"amount\":30.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = body(result).path("data");
        assertEquals(appointmentId, data.path("appointmentId").asLong());
        assertTrue(data.path("paymentId").asLong() > 0);
        assertEquals("MOCK", data.path("channel").asText());
        assertEquals("SUCCESS", data.path("status").asText());
        assertEquals("PAID", data.path("paymentStatus").asText());
        assertEquals("PAID", data.path("appointmentStatus").asText());
    }

    @Test
    void payRejectsAmountMismatch() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE);

        mockMvc.perform(postJson("/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken(), "{\"channel\":\"MOCK\",\"amount\":1.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("支付金额与应缴金额不一致"));
    }

    @Test
    void payRejectsNonMockChannel() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE);

        mockMvc.perform(postJson("/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken(), "{\"channel\":\"WECHAT\",\"amount\":30.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("支付金额与应缴金额不一致"));
    }

    @Test
    void payUnknownAppointmentReturns404() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(postJson("/api/v1/appointments/999999/payments",
                        patient.accessToken(), "{\"channel\":\"MOCK\",\"amount\":30.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("挂号单不存在或不属于当前患者"));
    }

    @Test
    void payOtherPatientAppointmentIsHidden() throws Exception {
        PatientSession owner = verifiedPatient(mockMvc, jdbcTemplate);
        PatientSession stranger = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createAppointment(mockMvc, owner.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE);

        mockMvc.perform(postJson("/api/v1/appointments/" + appointmentId + "/payments",
                        stranger.accessToken(), "{\"channel\":\"MOCK\",\"amount\":30.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("挂号单不存在或不属于当前患者"));
    }

    @Test
    void payTwiceIsRejected() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = AppointmentFixtures.createPaidAppointment(mockMvc,
                patient.accessToken(), AppointmentFixtures.DOCTOR_ONLINE,
                AppointmentFixtures.SCHEDULE_BOOKABLE, AppointmentFixtures.ONLINE_FEE);

        mockMvc.perform(postJson("/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken(), "{\"channel\":\"MOCK\",\"amount\":30.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前挂号单状态不允许该操作"));
    }

    @Test
    void payFailsWhenMockPaymentDisabled() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE);
        when(appointmentProperties.mockPaymentEnabled()).thenReturn(false);

        mockMvc.perform(postJson("/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken(), "{\"channel\":\"MOCK\",\"amount\":30.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("支付失败，请重试"));
    }

    @Test
    void payRequiresAuthentication() throws Exception {
        mockMvc.perform(postJson("/api/v1/appointments/1/payments", null,
                        "{\"channel\":\"MOCK\",\"amount\":30.00}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void queryUnpaidReturnsUnpaidStatusWithNullDetails() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = createAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE);

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = body(result).path("data");
        assertEquals("UNPAID", data.path("status").asText());
        assertEquals("UNPAID", data.path("paymentStatus").asText());
        assertEquals("PENDING_PAYMENT", data.path("appointmentStatus").asText());
        assertTrue(data.path("paymentId").isNull());
        assertTrue(data.path("paidAt").isNull());
    }

    @Test
    void queryPaidReturnsRecordDetails() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = AppointmentFixtures.createPaidAppointment(mockMvc,
                patient.accessToken(), AppointmentFixtures.DOCTOR_ONLINE,
                AppointmentFixtures.SCHEDULE_BOOKABLE, AppointmentFixtures.ONLINE_FEE);

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken()))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = body(result).path("data");
        assertEquals("SUCCESS", data.path("status").asText());
        assertEquals("MOCK", data.path("channel").asText());
        assertEquals(30.00, data.path("amount").asDouble(), 0.001);
        assertEquals("PAID", data.path("appointmentStatus").asText());
    }

    @Test
    void queryUnknownAppointmentReturns404() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(get("/api/v1/appointments/999999/payments", patient.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
