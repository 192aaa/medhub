package com.sdas.server.doctor.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.doctor.dto.AiDecisionResponse;
import com.sdas.server.doctor.entity.AiDecisionSession;
import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.doctor.repository.AiDecisionSessionRepository;
import com.sdas.server.doctor.service.AiDecisionClient;
import com.sdas.server.doctor.service.AiDecisionResult;
import com.sdas.server.doctor.service.impl.AiDecisionServiceImpl;
import com.sdas.server.identity.service.AuditEventService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** AiDecisionService 单测（T069）：成功、超时降级、越权拒绝。 */
class AiDecisionServiceTest {

    private ConsultationRepository consultationRepository;
    private AiDecisionClient aiDecisionClient;
    private AiDecisionSessionRepository sessionRepository;
    private AiDecisionServiceImpl service;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationRepository.class);
        aiDecisionClient = mock(AiDecisionClient.class);
        sessionRepository = mock(AiDecisionSessionRepository.class);
        AuditEventService auditEventService = mock(AuditEventService.class);
        service = new AiDecisionServiceImpl(consultationRepository, aiDecisionClient,
                sessionRepository, auditEventService);
    }

    @Test
    void decideSuccessReturnsSuggestions() {
        Consultation consultation = consultation(701L, 101L, ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(consultation));
        when(aiDecisionClient.decide(anyString()))
                .thenReturn(new AiDecisionResult(true, List.of("疑似上呼吸道感染"), "DeepSeek"));
        when(sessionRepository.insert(org.mockito.ArgumentMatchers.any(AiDecisionSession.class)))
                .thenReturn(1101L);

        AiDecisionResponse response = service.decide(101L, 701L, null);
        assertEquals("SUCCESS", response.status());
        assertEquals(1, response.suggestions().size());
        assertEquals(AiDecisionServiceImpl.DISCLAIMER, response.disclaimer());
    }

    @Test
    void decideTimeoutFallsBackToEmptySuggestions() {
        Consultation consultation = consultation(701L, 101L, ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(consultation));
        when(aiDecisionClient.decide(anyString())).thenReturn(AiDecisionResult.timeout());
        when(sessionRepository.insert(org.mockito.ArgumentMatchers.any(AiDecisionSession.class)))
                .thenReturn(1102L);

        AiDecisionResponse response = service.decide(101L, 701L, null);
        assertEquals("TIMEOUT_FALLBACK", response.status());
        assertTrue(response.suggestions().isEmpty());
    }

    @Test
    void decideForbiddenWhenNotOwner() {
        Consultation consultation = consultation(701L, 102L, ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(consultation));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.decide(101L, 701L, null));
        assertEquals(ErrorCode.CONSULTATION_FORBIDDEN, exception.getErrorCode());
    }

    private Consultation consultation(long id, long doctorId, ConsultationStatus status) {
        return new Consultation(id, 1L, 10001L, doctorId, 1L, "咳嗽三天", null, null, status,
                null, null, ConsultationTimeoutStatus.NONE, null, null, null, null, 0);
    }
}
