package com.sdas.server.appointment.contract;

import static com.sdas.server.appointment.support.AppointmentFixtures.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdas.server.appointment.support.AppointmentFixtures;
import com.sdas.server.appointment.support.AppointmentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 端点 2/3 契约测试（tasks.md T026）：医生列表筛选、空结果、详情含排班、异常码。
 */
class DoctorControllerContractTest extends AppointmentTestSupport {

    @BeforeEach
    void setUp() {
        AppointmentFixtures.seedCatalog(jdbcTemplate);
    }

    @Test
    void listFilteredByDepartmentReturnsOnlyThatDepartment() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/doctors?departmentId="
                        + AppointmentFixtures.DEPT_INTERNAL, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = AppointmentFixtures.body(result).path("data");
        assertTrue(contains(data, AppointmentFixtures.DOCTOR_ONLINE), "应包含内科在线医生");
        assertTrue(!contains(data, AppointmentFixtures.DOCTOR_OFFLINE), "不应包含其他科室医生");
    }

    @Test
    void listFilteredByOnlineStatus() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/doctors?online=ONLINE", null))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = AppointmentFixtures.body(result).path("data");
        assertTrue(contains(data, AppointmentFixtures.DOCTOR_ONLINE));
        assertTrue(!contains(data, AppointmentFixtures.DOCTOR_OFFLINE));
        data.forEach(node -> assertEquals("ONLINE", node.path("onlineStatus").asText()));
    }

    @Test
    void unknownOnlineValueIsIgnored() throws Exception {
        mockMvc.perform(get("/api/v1/doctors?online=NOT_A_STATUS", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void emptyDepartmentReturnsEmptyArray() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/doctors?departmentId=999999", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        assertEquals(0, AppointmentFixtures.body(result).path("data").size());
    }

    @Test
    void detailReturnsDoctorAndSchedules() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/doctors/" + AppointmentFixtures.DOCTOR_ONLINE, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.doctorId").value(AppointmentFixtures.DOCTOR_ONLINE))
                .andReturn();

        JsonNode data = AppointmentFixtures.body(result).path("data");
        assertEquals(30.00, data.path("consultationFee").asDouble(), 0.001);
        assertEquals(AppointmentFixtures.DEPT_INTERNAL, data.path("departmentId").asLong());
        JsonNode schedules = data.path("schedule");
        assertTrue(schedules.isArray() && !schedules.isEmpty(), "详情须返回排班数组");
        boolean bookableFound = false;
        for (JsonNode schedule : schedules) {
            if (schedule.path("scheduleId").asLong() == AppointmentFixtures.SCHEDULE_BOOKABLE) {
                bookableFound = true;
                assertEquals("MORNING", schedule.path("period").asText());
                assertEquals(20, schedule.path("slotsTotal").asInt());
            }
        }
        assertTrue(bookableFound, "应包含可约排班");
    }

    @Test
    void detailUnknownDoctorReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/999999", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("医生不存在"));
    }

    @Test
    void detailNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/not-a-number", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private boolean contains(JsonNode data, long doctorId) {
        for (JsonNode node : data) {
            if (node.path("doctorId").asLong() == doctorId) {
                assertNotNull(node.path("name"));
                return true;
            }
        }
        return false;
    }
}
