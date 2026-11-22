package com.sdas.server.doctor.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.TreatmentDecisionProperties;
import com.sdas.server.doctor.dto.CaseSummaryView;
import com.sdas.server.doctor.dto.TreatmentDecisionResponse;
import com.sdas.server.doctor.entity.AiDecisionSession;
import com.sdas.server.doctor.entity.DrugCatalog;
import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.model.TreatmentDecisionStatus;
import com.sdas.server.doctor.model.TreatmentTriggerType;
import com.sdas.server.doctor.repository.AiDecisionSessionRepository;
import com.sdas.server.doctor.repository.ConsultationMessageRepository;
import com.sdas.server.doctor.repository.DrugCatalogRepository;
import com.sdas.server.doctor.repository.PrescriptionRepository;
import com.sdas.server.doctor.service.TreatmentDecisionClient;
import com.sdas.server.doctor.service.TreatmentDecisionResult;
import com.sdas.server.doctor.service.impl.TreatmentDecisionServiceImpl;
import com.sdas.server.doctor.support.DecisionContext;
import com.sdas.server.doctor.support.DecisionContextAssembler;
import com.sdas.server.doctor.support.DecisionPayloadParser;
import com.sdas.server.doctor.support.DecisionPromptBuilder;
import com.sdas.server.doctor.support.DecisionResult;
import com.sdas.server.doctor.support.MedicationMapper;
import com.sdas.server.doctor.support.TreatmentDecisionDisclaimer;
import com.sdas.server.doctor.support.TreatmentLimitStore;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 治疗决策编排：降级留痕、成功链路、幂等复用、采纳回填与状态限制。 */
class TreatmentDecisionServiceTest {

    private static final long DOCTOR_ID = 101L;

    private static final long CONSULTATION_ID = 501L;

    private ConsultationRepository consultationRepository;

    private PrescriptionRepository prescriptionRepository;

    private ConsultationMessageRepository messageRepository;

    private AiDecisionSessionRepository sessionRepository;

    private DrugCatalogRepository drugCatalogRepository;

    private TreatmentDecisionClient client;

    private DecisionContextAssembler assembler;

    private TreatmentLimitStore limitStore;

    private AuditEventService auditEventService;

    private ObjectMapper objectMapper;

    private TreatmentDecisionServiceImpl service;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationRepository.class);
        prescriptionRepository = mock(PrescriptionRepository.class);
        messageRepository = mock(ConsultationMessageRepository.class);
        sessionRepository = mock(AiDecisionSessionRepository.class);
        drugCatalogRepository = mock(DrugCatalogRepository.class);
        client = mock(TreatmentDecisionClient.class);
        assembler = mock(DecisionContextAssembler.class);
        limitStore = mock(TreatmentLimitStore.class);
        auditEventService = mock(AuditEventService.class);
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        TreatmentDecisionProperties properties =
                new TreatmentDecisionProperties(null, 0, 0, 0, 0, 0, 5, 10, 3, 30);
        DecisionPromptBuilder promptBuilder = mock(DecisionPromptBuilder.class);
        when(promptBuilder.systemPrompt()).thenReturn("system");
        when(promptBuilder.build(any())).thenReturn("user");
        when(limitStore.lock(CONSULTATION_ID))
                .thenReturn(new TreatmentLimitStore.LockOutcome("token", true, true));
        when(consultationRepository.findById(CONSULTATION_ID))
                .thenReturn(Optional.of(consultation(ConsultationStatus.IN_PROGRESS)));
        when(assembler.assemble(anyLong(), anyLong())).thenReturn(context());
        when(sessionRepository.insert(any(AiDecisionSession.class))).thenReturn(1201L);
        service = new TreatmentDecisionServiceImpl(consultationRepository, prescriptionRepository,
                messageRepository, sessionRepository, drugCatalogRepository, client, assembler,
                promptBuilder, new DecisionPayloadParser(properties), new MedicationMapper(),
                limitStore, auditEventService, properties, objectMapper);
    }

    @Test
    @DisplayName("超时降级仍落 1 条留痕且摘要为空、免责照常")
    void degradesAndKeepsTrace() {
        when(client.decide(any())).thenReturn(
                TreatmentDecisionResult.degraded(DegradedReason.TIMEOUT));

        TreatmentDecisionResponse response = service.generate(DOCTOR_ID, CONSULTATION_ID,
                TreatmentTriggerType.AUTO);

        assertThat(response.status()).isEqualTo(TreatmentDecisionStatus.TIMEOUT_FALLBACK);
        assertThat(response.summary()).isNull();
        assertThat(response.suggestions()).isEmpty();
        assertThat(response.medicationDrafts()).isEmpty();
        assertThat(response.disclaimer()).isEqualTo(TreatmentDecisionDisclaimer.TEXT);
        assertThat(response.degradedReason()).isEqualTo(DegradedReason.TIMEOUT);
        AiDecisionSession saved = captureInsert();
        assertThat(saved.status()).isEqualTo(TreatmentDecisionStatus.TIMEOUT_FALLBACK);
        assertThat(saved.degradedReason()).isEqualTo(DegradedReason.TIMEOUT);
        assertThat(saved.resultJson()).contains("TIMEOUT");
        verify(auditEventService).record(eq(AuditEventType.AI_TREATMENT_DECISION_DEGRADED),
                eq("FAILURE"), any(), any(), any(), eq("TIMEOUT"));
    }

    @Test
    @DisplayName("成功链路返回摘要/建议/草稿并落 SUCCESS 留痕")
    void successMapsPayload() {
        when(client.decide(any())).thenReturn(TreatmentDecisionResult.success(successJson(),
                "test-model"));
        when(drugCatalogRepository.findEnabledByNames(any())).thenReturn(List.of(drug()));

        TreatmentDecisionResponse response = service.generate(DOCTOR_ID, CONSULTATION_ID,
                TreatmentTriggerType.AUTO);

        assertThat(response.status()).isEqualTo(TreatmentDecisionStatus.SUCCESS);
        assertThat(response.summary()).isNotNull();
        assertThat(response.summary().evidenceLimited()).isFalse();
        assertThat(response.suggestions()).hasSize(1);
        assertThat(response.medicationDrafts()).hasSize(1);
        assertThat(response.medicationDrafts().get(0).drugId()).isEqualTo(901L);
        assertThat(response.disclaimer()).isEqualTo(TreatmentDecisionDisclaimer.TEXT);
        assertThat(captureInsert().status()).isEqualTo(TreatmentDecisionStatus.SUCCESS);
    }

    @Test
    @DisplayName("同指纹命中复用时不调用模型、elapsedMs=0")
    void reusesExistingDecision() throws Exception {
        String resultJson = objectMapper.writeValueAsString(new DecisionResult(
                new CaseSummaryView("主诉", "现病史", List.of(), List.of(), false,
                        LocalDateTime.now(), "test-model"),
                List.of(), List.of(), List.of(), DegradedReason.NONE));
        AiDecisionSession stored = new AiDecisionSession(1201L, CONSULTATION_ID, DOCTOR_ID,
                "summary", null, "test-model", TreatmentDecisionStatus.SUCCESS, 1200,
                LocalDateTime.now(), TreatmentTriggerType.AUTO, "fingerprint", resultJson,
                DegradedReason.NONE, null, null);
        when(sessionRepository.findReusable(eq(CONSULTATION_ID), eq(DOCTOR_ID), anyString(),
                any())).thenReturn(Optional.of(stored));

        TreatmentDecisionResponse response = service.generate(DOCTOR_ID, CONSULTATION_ID,
                TreatmentTriggerType.AUTO);

        assertThat(response.reused()).isTrue();
        assertThat(response.elapsedMs()).isZero();
        assertThat(response.summary().chiefComplaint()).isEqualTo("主诉");
        verify(client, never()).decide(any());
        verify(sessionRepository, never()).insert(any(AiDecisionSession.class));
    }

    @Test
    @DisplayName("已开方时返回 PRESCRIPTION_ALREADY_EXISTS")
    void rejectsWhenPrescriptionExists() {
        when(prescriptionRepository.findByConsultationId(CONSULTATION_ID))
                .thenReturn(Optional.of(mock(com.sdas.server.doctor.entity.Prescription.class)));

        assertThatThrownBy(() -> service.generate(DOCTOR_ID, CONSULTATION_ID,
                TreatmentTriggerType.AUTO))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.PRESCRIPTION_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("非接诊医生返回 CONSULTATION_FORBIDDEN")
    void rejectsOtherDoctor() {
        assertThatThrownBy(() -> service.latest(999L, CONSULTATION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONSULTATION_FORBIDDEN));
    }

    @Test
    @DisplayName("latest 无记录抛 TREATMENT_DECISION_NOT_FOUND")
    void latestWithoutRecordFails() {
        when(sessionRepository.findLatestByConsultationIdAndDoctorId(CONSULTATION_ID, DOCTOR_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.latest(DOCTOR_ID, CONSULTATION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.TREATMENT_DECISION_NOT_FOUND));
    }

    @Test
    @DisplayName("采纳数量越界返回 INVALID_TREATMENT_ADOPTION")
    void rejectsInvalidAdoptionCount() {
        assertThatThrownBy(() -> service.markAdopted(DOCTOR_ID, CONSULTATION_ID, 21))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TREATMENT_ADOPTION));
    }

    @Test
    @DisplayName("采纳回填最近一条记录并写审计")
    void marksAdopted() {
        AiDecisionSession stored = new AiDecisionSession(1201L, CONSULTATION_ID, DOCTOR_ID,
                "summary", null, "test-model", TreatmentDecisionStatus.SUCCESS, 1200,
                LocalDateTime.now(), TreatmentTriggerType.AUTO, "fingerprint", null,
                DegradedReason.NONE, null, null);
        when(sessionRepository.findLatestByConsultationIdAndDoctorId(CONSULTATION_ID, DOCTOR_ID))
                .thenReturn(Optional.of(stored));

        service.markAdopted(DOCTOR_ID, CONSULTATION_ID, 2);

        verify(sessionRepository).markAdopted(eq(1201L), eq(2), any());
        verify(auditEventService).record(eq(AuditEventType.AI_TREATMENT_DECISION_ADOPTED),
                eq("SUCCESS"), any(), any(), any(), eq("2"));
    }

    private AiDecisionSession captureInsert() {
        ArgumentCaptor<AiDecisionSession> captor = ArgumentCaptor.forClass(AiDecisionSession.class);
        verify(sessionRepository, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getValue();
    }

    private static DecisionContext context() {
        return new DecisionContext("咳嗽 3 天", "受凉后咳嗽", List.of(), 0, false, List.of());
    }

    private static Consultation consultation(ConsultationStatus status) {
        return new Consultation(CONSULTATION_ID, 1L, 10001L, DOCTOR_ID, 7L, "咳嗽 3 天",
                "受凉后咳嗽", "摘要", status, null, null, ConsultationTimeoutStatus.NONE, null,
                null, LocalDateTime.now(), LocalDateTime.now(), 0);
    }

    private static DrugCatalog drug() {
        return new DrugCatalog(901L, "阿莫西林胶囊", "0.25g*20", "示例药厂", "胶囊", "盒", true,
                true, "PRESET", null, null, 0);
    }

    private static String successJson() {
        return "{\"summary\":{\"chiefComplaint\":\"咳嗽 3 天\","
                + "\"presentIllness\":\"受凉后咳嗽\",\"dialogHighlights\":[],"
                + "\"riskAlerts\":[]},"
                + "\"suggestions\":[{\"diagnosis\":\"疑似上呼吸道感染\","
                + "\"likelihood\":\"MEDIUM\",\"basis\":[\"咳嗽 3 天\"],"
                + "\"workup\":[],\"riskAlert\":null}],"
                + "\"medications\":[{\"drugName\":\"阿莫西林胶囊\","
                + "\"dosage\":\"口服，一次 2 粒\",\"caution\":\"青霉素过敏者禁用\"}]}";
    }
}
