package com.sdas.server.appointment.integration;

import static com.sdas.server.appointment.support.AppointmentFixtures.appointmentCount;
import static com.sdas.server.appointment.support.AppointmentFixtures.auditCount;
import static com.sdas.server.appointment.support.AppointmentFixtures.body;
import static com.sdas.server.appointment.support.AppointmentFixtures.createAppointment;
import static com.sdas.server.appointment.support.AppointmentFixtures.get;
import static com.sdas.server.appointment.support.AppointmentFixtures.postJson;
import static com.sdas.server.appointment.support.AppointmentFixtures.verifiedPatient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdas.server.appointment.model.AppointmentStatus;
import com.sdas.server.appointment.model.PaymentStatus;
import com.sdas.server.appointment.support.AppointmentFixtures;
import com.sdas.server.appointment.support.AppointmentFixtures.PatientSession;
import com.sdas.server.appointment.support.AppointmentTestSupport;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 挂号支付主流程集成测试（tasks.md T028）：金额快照、状态流转、幂等、门禁拒绝与审计。
 */
class AppointmentPaymentFlowIT extends AppointmentTestSupport {

    @BeforeEach
    void setUp() {
        AppointmentFixtures.seedCatalog(jdbcTemplate);
    }

    @Test
    void bookingAndPaymentFlowPersistsConsistentState() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        long appointmentId = createAppointment(mockMvc, patient.accessToken(),
                AppointmentFixtures.DOCTOR_ONLINE, AppointmentFixtures.SCHEDULE_BOOKABLE);

        Map<String, Object> created = jdbcTemplate.queryForMap(
                "SELECT fee_amount, payment_status, appointment_status, version FROM appointment "
                        + "WHERE id = ?", appointmentId);
        assertEquals(0, new BigDecimal("30.00").compareTo((BigDecimal) created.get("fee_amount")),
                "金额须快照医生咨询费");
        assertEquals(PaymentStatus.UNPAID.name(), created.get("payment_status"));
        assertEquals(AppointmentStatus.PENDING_PAYMENT.name(), created.get("appointment_status"));
        assertEquals(0, ((Number) created.get("version")).intValue());

        MvcResult payment = mockMvc.perform(postJson(
                        "/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken(), "{\"channel\":\"MOCK\",\"amount\":30.00}"))
                .andExpect(status().isOk())
                .andReturn();
        long paymentId = body(payment).path("data").path("paymentId").asLong();

        Map<String, Object> pending = jdbcTemplate.queryForMap(
                "SELECT payment_status, appointment_status, version FROM appointment WHERE id = ?",
                appointmentId);
        assertEquals(PaymentStatus.UNPAID.name(), pending.get("payment_status"));
        assertEquals(AppointmentStatus.PENDING_PAYMENT.name(), pending.get("appointment_status"));
        Map<String, Object> pendingRecord = jdbcTemplate.queryForMap(
                "SELECT status FROM payment_record WHERE appointment_id = ?", appointmentId);
        assertEquals("PENDING", pendingRecord.get("status"));

        mockMvc.perform(postJson("/api/v1/appointments/" + appointmentId + "/payments/"
                        + paymentId + "/confirm", patient.accessToken(), ""))
                .andExpect(status().isOk());

        Map<String, Object> paid = jdbcTemplate.queryForMap(
                "SELECT payment_status, appointment_status, version FROM appointment WHERE id = ?",
                appointmentId);
        assertEquals(PaymentStatus.PAID.name(), paid.get("payment_status"));
        assertEquals(AppointmentStatus.PAID.name(), paid.get("appointment_status"));
        assertEquals(1, ((Number) paid.get("version")).intValue());

        Map<String, Object> record = jdbcTemplate.queryForMap(
                "SELECT channel, amount, status FROM payment_record WHERE appointment_id = ?",
                appointmentId);
        assertEquals("MOCK", record.get("channel"));
        assertEquals(0, new BigDecimal("30.00").compareTo((BigDecimal) record.get("amount")));
        assertEquals("SUCCESS", record.get("status"));

        MvcResult queried = mockMvc.perform(get(
                        "/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken()))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = body(queried).path("data");
        assertEquals("PAID", data.path("paymentStatus").asText());
        assertEquals("PAID", data.path("appointmentStatus").asText());

        assertTrue(auditCount(jdbcTemplate, "APPOINTMENT_CREATED", patient.patientId()) >= 1);
        assertTrue(auditCount(jdbcTemplate, "APPOINTMENT_PAID", patient.patientId()) >= 1);
    }

    @Test
    void repeatedPaymentKeepsSingleRecord() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);
        long appointmentId = AppointmentFixtures.createPaidAppointment(mockMvc,
                patient.accessToken(), AppointmentFixtures.DOCTOR_ONLINE,
                AppointmentFixtures.SCHEDULE_BOOKABLE, AppointmentFixtures.ONLINE_FEE);

        mockMvc.perform(postJson("/api/v1/appointments/" + appointmentId + "/payments",
                        patient.accessToken(), "{\"channel\":\"MOCK\",\"amount\":30.00}"))
                .andExpect(status().is4xxClientError());

        Integer records = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM payment_record WHERE appointment_id = ?", Integer.class,
                appointmentId);
        assertEquals(1, records == null ? 0 : records.intValue());
        assertEquals(1, auditCount(jdbcTemplate, "APPOINTMENT_PAID", patient.patientId()));
    }

    @Test
    void offlineDoctorCreatesNoAppointment() throws Exception {
        PatientSession patient = verifiedPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(postJson("/api/v1/appointments", patient.accessToken(),
                        "{\"doctorId\":1104,\"scheduleId\":1203}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, appointmentCount(jdbcTemplate, patient.patientId()));
    }

    @Test
    void ineligiblePatientCreatesNoAppointmentAndAuditsDenial() throws Exception {
        PatientSession patient = AppointmentFixtures.newPatient(mockMvc, jdbcTemplate);

        mockMvc.perform(postJson("/api/v1/appointments", patient.accessToken(),
                        "{\"doctorId\":1101,\"scheduleId\":1201}"))
                .andExpect(status().isForbidden());

        assertEquals(0, appointmentCount(jdbcTemplate, patient.patientId()));
        assertTrue(auditCount(jdbcTemplate, "REVISIT_GATE_DENIED", patient.patientId()) >= 1);
    }
}
