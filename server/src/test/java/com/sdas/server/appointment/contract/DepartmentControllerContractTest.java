package com.sdas.server.appointment.contract;

import static com.sdas.server.appointment.support.AppointmentFixtures.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdas.server.appointment.support.AppointmentFixtures;
import com.sdas.server.appointment.support.AppointmentTestSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 端点 1 契约测试（tasks.md T025）：启用科室列表、字段结构、停用过滤与排序。
 */
class DepartmentControllerContractTest extends AppointmentTestSupport {

    @BeforeEach
    void setUp() {
        AppointmentFixtures.seedCatalog(jdbcTemplate);
    }

    @Test
    void listReturnsEnabledDepartmentsWithContractFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/departments", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode data = AppointmentFixtures.body(result).path("data");
        JsonNode internal = findByDepartmentId(data, AppointmentFixtures.DEPT_INTERNAL);
        assertNotNull(internal, "启用科室（内科）应出现在列表中");
        assertEquals("内科", internal.path("name").asText());
        assertEquals(1, internal.path("sortOrder").asInt());
        assertTrue(internal.path("enabled").asBoolean());
        assertNull(findByDepartmentId(data, AppointmentFixtures.DEPT_DISABLED),
                "停用科室不应出现在列表");
    }

    @Test
    void listIsSortedBySortOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/departments", null))
                .andExpect(status().isOk()).andReturn();

        List<Integer> orders = new ArrayList<>();
        AppointmentFixtures.body(result).path("data")
                .forEach(node -> orders.add(node.path("sortOrder").asInt()));
        List<Integer> sorted = new ArrayList<>(orders);
        sorted.sort(Integer::compareTo);
        assertEquals(sorted, orders, "科室须按 sortOrder 升序返回");
    }

    private JsonNode findByDepartmentId(JsonNode data, long departmentId) {
        for (JsonNode node : data) {
            if (node.path("departmentId").asLong() == departmentId) {
                return node;
            }
        }
        return null;
    }
}
