package com.sdas.server.doctor.integration;

import static com.sdas.server.doctor.contract.DoctorContractFixtures.doctorLogin;
import static com.sdas.server.doctor.contract.DoctorContractFixtures.seedDoctor;
import static com.sdas.server.doctor.contract.DoctorContractFixtures.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 降级链路集成验收（T029，US4）：模型超时仍返回 200 并留痕，随后 003 开方成功（SC-006）；
 * 刷新走端点 2 返回同一条降级记录而非重新调用模型。
 */
class TreatmentDecisionFlowIT extends DoctorTestSupport {

    private static final String DECISION_PATH = "/api/v1/workbench/consultations/501"
            + "/treatment-decision";

    private static final String PRESCRIPTION_PATH = "/api/v1/workbench/consultations/501"
            + "/prescriptions";

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        redisTemplate.delete(List.of("ai:treatment:lock:501", "ai:treatment:rate:501"));
        jdbcTemplate.update("DELETE FROM prescription_item WHERE prescription_id IN "
                + "(SELECT id FROM prescription WHERE consultation_id = ?)", 501L);
        jdbcTemplate.update("DELETE FROM prescription WHERE consultation_id = ?", 501L);
        jdbcTemplate.update("DELETE FROM ai_decision_session WHERE consultation_id = ?", 501L);
        seedDoctor(jdbcTemplate);
        seedConsultationInProgress(501L, 1001L, 101L);
        seedConsultationStatus(501L, "IN_PROGRESS");
        seedDrugs();
        seedDialog(501L, "咳嗽三天，夜间加重", "无发热");
        token = doctorLogin(mockMvc);
    }

    @Test
    @DisplayName("降级后仍可开方成功，且刷新不重复调用模型")
    void degradedThenPrescribeSucceeds() throws Exception {
        when(treatmentDecisionClient.decide(any()))
                .thenReturn(TreatmentDecisionResult.degraded(DegradedReason.TIMEOUT));

        mockMvc.perform(post(DECISION_PATH, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("TIMEOUT_FALLBACK"))
                .andExpect(jsonPath("$.data.summary").value(nullValue()))
                .andExpect(jsonPath("$.data.suggestions").isEmpty());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_decision_session WHERE consultation_id = 501",
                Integer.class);
        assertThat(rows).isEqualTo(1);

        mockMvc.perform(MockMvcRequestBuilders.get(DECISION_PATH)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TIMEOUT_FALLBACK"));
        verify(treatmentDecisionClient, times(1)).decide(any());

        mockMvc.perform(post(PRESCRIPTION_PATH,
                        "{\"items\":[{\"drugId\":901,\"dosage\":\"每日三次，每次一片\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SIGNED"))
                .andExpect(jsonPath("$.data.items[0].drugId").value(901));
    }

    private MockHttpServletRequestBuilder post(String path, String body) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.content(body);
        }
        withCsrf(builder);
        return builder;
    }
}
