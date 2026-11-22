package com.sdas.server.healthconsultation.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.config.HealthProperties;
import com.sdas.server.healthconsultation.dto.ChatRequest;
import com.sdas.server.healthconsultation.dto.HealthSseEvents;
import com.sdas.server.healthconsultation.entity.HealthMessage;
import com.sdas.server.healthconsultation.entity.HealthSession;
import com.sdas.server.healthconsultation.model.EmergencyVerdict;
import com.sdas.server.healthconsultation.model.SessionStatus;
import com.sdas.server.healthconsultation.repository.HealthMessageRepository;
import com.sdas.server.healthconsultation.repository.HealthSessionRepository;
import com.sdas.server.healthconsultation.service.EmergencySymptomService;
import com.sdas.server.healthconsultation.service.impl.HealthChatServiceImpl;
import com.sdas.server.healthconsultation.support.EmergencyDecisionResolver;
import com.sdas.server.healthconsultation.support.EmergencyHeaderParser.ModelEmergencySignal;
import com.sdas.server.healthconsultation.support.HealthChatClient;
import com.sdas.server.healthconsultation.support.HealthChatCommand;
import com.sdas.server.healthconsultation.support.HealthChatException;
import com.sdas.server.healthconsultation.support.HealthPromptBuilder;
import com.sdas.server.healthconsultation.support.PromptResult;
import com.sdas.server.healthconsultation.support.RedisDialogStore;
import com.sdas.server.healthconsultation.support.SseEventWriter;
import com.sdas.server.identity.service.AuditEventService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletResponse;
import reactor.core.publisher.Flux;

/** 流式编排：事件顺序、预警优先、失败降级。 */
class HealthChatServiceImplTest {

    private static final long PATIENT_ID = 7L;

    private static final long SESSION_ID = 1L;

    private final HealthSessionRepository sessionRepository = mock(HealthSessionRepository.class);

    private final HealthMessageRepository messageRepository = mock(HealthMessageRepository.class);

    private final RedisDialogStore redisDialogStore = mock(RedisDialogStore.class);

    private final HealthPromptBuilder promptBuilder = mock(HealthPromptBuilder.class);

    private final HealthChatClient chatClient = mock(HealthChatClient.class);

    private final EmergencySymptomService emergencySymptomService =
            mock(EmergencySymptomService.class);

    private final SseEventWriter sseEventWriter = mock(SseEventWriter.class);

    private final AuditEventService auditEventService = mock(AuditEventService.class);

    private final HealthChatServiceImpl service = new HealthChatServiceImpl(sessionRepository,
            messageRepository, redisDialogStore, promptBuilder, chatClient, emergencySymptomService,
            sseEventWriter, auditEventService, properties());

    private final CountDownLatch finished = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        HealthSession session = new HealthSession(SESSION_ID, PATIENT_ID, "新对话",
                LocalDateTime.now(), 0, SessionStatus.ACTIVE, LocalDateTime.now(),
                LocalDateTime.now(), 0);
        when(sessionRepository.findByIdAndPatientId(SESSION_ID, PATIENT_ID))
                .thenReturn(Optional.of(session));
        when(redisDialogStore.lock(SESSION_ID, 65))
                .thenReturn(new RedisDialogStore.LockOutcome("token", true, true));
        when(redisDialogStore.window(SESSION_ID)).thenReturn(List.of());
        when(messageRepository.findRecentCompleted(anyLong(), anyInt())).thenReturn(List.of());
        when(messageRepository.insert(any(HealthMessage.class))).thenReturn(90001L, 90002L);
        when(promptBuilder.build(anyLong(), any(), anyString()))
                .thenReturn(new PromptResult(command(), false));
        doAnswer(invocation -> {
            finished.countDown();
            return null;
        }).when(sseEventWriter).done(any(), anyLong(), eq(false), anyString());
    }

    @Test
    @DisplayName("紧急时 emergency 必须先于首个 delta")
    void emergencyBeforeDelta() throws Exception {
        when(chatClient.stream(any())).thenReturn(Flux.just(
                "EMERGENCY: YES\n", "请立即拨打120。"));
        when(emergencySymptomService.evaluate(eq(SESSION_ID), anyString(),
                eq(ModelEmergencySignal.YES))).thenReturn(evaluation(true));

        service.chat(PATIENT_ID, SESSION_ID, new ChatRequest("胸口疼得厉害，喘不上气"),
                new MockHttpServletResponse());

        finished.await(3, TimeUnit.SECONDS);
        InOrder order = inOrder(sseEventWriter);
        order.verify(sseEventWriter).accepted(any(), eq(90001L), eq(SESSION_ID));
        order.verify(sseEventWriter).emergency(any(), any(HealthSseEvents.Emergency.class));
        order.verify(sseEventWriter).delta(any(), anyString());
        order.verify(sseEventWriter).disclaimer(any());
        order.verify(sseEventWriter).done(any(), eq(90002L), eq(false), anyString());
    }

    @Test
    @DisplayName("普通问答只发 delta 与免责声明，不预警")
    void normalFlow() throws Exception {
        when(chatClient.stream(any())).thenReturn(Flux.just("EMERGENCY: NO\n", "多饮水休息。"));
        when(emergencySymptomService.evaluate(eq(SESSION_ID), anyString(),
                eq(ModelEmergencySignal.NO))).thenReturn(evaluation(false));

        service.chat(PATIENT_ID, SESSION_ID, new ChatRequest("有点困"), new MockHttpServletResponse());

        finished.await(3, TimeUnit.SECONDS);
        verify(sseEventWriter, org.mockito.Mockito.never()).emergency(any(), any());
        verify(sseEventWriter).disclaimer(any());
    }

    @Test
    @DisplayName("模型失败以 error 结束且助手消息标记 FAILED")
    void streamFailure() throws Exception {
        when(chatClient.stream(any())).thenReturn(Flux.error(
                new HealthChatException(HealthChatException.Reason.TIMEOUT, null)));
        when(emergencySymptomService.evaluate(anyLong(), anyString(), any()))
                .thenReturn(evaluation(false));
        doAnswer(invocation -> {
            finished.countDown();
            return null;
        }).when(sseEventWriter).complete(any());

        service.chat(PATIENT_ID, SESSION_ID, new ChatRequest("咳嗽"), new MockHttpServletResponse());

        finished.await(3, TimeUnit.SECONDS);
        verify(sseEventWriter).error(any(), eq(com.sdas.server.common.ErrorCode
                .AI_SERVICE_UNAVAILABLE), eq(true));
        verify(auditEventService).record(
                eq(com.sdas.server.identity.model.AuditEventType.AI_HEALTH_CHAT_FAILED),
                eq("FAILED"), eq(PATIENT_ID), any(), any(), eq("TIMEOUT"));
    }

    private EmergencySymptomService.Evaluation evaluation(boolean warn) {
        EmergencyDecisionResolver.Result result = new EmergencyDecisionResolver.Result(
                warn ? EmergencyVerdict.CONFIRMED : EmergencyVerdict.REJECTED, warn,
                warn ? "CHEST_PAIN_DYSPNEA" : "NONE");
        HealthSseEvents.Emergency payload = warn ? new HealthSseEvents.Emergency("HIGH",
                "症状可能存在紧急风险", "请立即拨打 120 或前往最近急诊",
                "CHEST_PAIN_DYSPNEA", null) : null;
        return new EmergencySymptomService.Evaluation(1L, result, payload);
    }

    private HealthChatCommand command() {
        return new HealthChatCommand("system", List.of(), 1024, "deepseek-v4-flash");
    }

    private static HealthProperties properties() {
        return new HealthProperties("deepseek-v4-flash", 8000, 3000, 1024, 6000, 1500, 12, 2000,
                200, 5);
    }
}
