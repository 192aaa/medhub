package com.sdas.server.healthconsultation.service.impl;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.HealthProperties;
import com.sdas.server.healthconsultation.dto.ChatRequest;
import com.sdas.server.healthconsultation.entity.HealthMessage;
import com.sdas.server.healthconsultation.entity.HealthSession;
import com.sdas.server.healthconsultation.model.MessageRole;
import com.sdas.server.healthconsultation.model.StreamStatus;
import com.sdas.server.healthconsultation.repository.HealthMessageRepository;
import com.sdas.server.healthconsultation.repository.HealthSessionRepository;
import com.sdas.server.healthconsultation.service.EmergencySymptomService;
import com.sdas.server.healthconsultation.service.HealthChatService;
import com.sdas.server.healthconsultation.support.ChatTurn;
import com.sdas.server.healthconsultation.support.ClientDisconnectedException;
import com.sdas.server.healthconsultation.support.DialogMeta;
import com.sdas.server.healthconsultation.support.EmergencyHeaderParser;
import com.sdas.server.healthconsultation.support.HealthChatClient;
import com.sdas.server.healthconsultation.support.HealthChatException;
import com.sdas.server.healthconsultation.support.HealthDisclaimer;
import com.sdas.server.healthconsultation.support.HealthPromptBuilder;
import com.sdas.server.healthconsultation.support.PromptResult;
import com.sdas.server.healthconsultation.support.RedisDialogStore;
import com.sdas.server.healthconsultation.support.SseEventWriter;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 流式问答编排实现（plan.md「一轮对话完整时序」）。
 *
 * <p>同步阶段（Tomcat 线程）：归属校验 → 长度/条数校验 → 抢锁 → 读窗口 → 落库患者消息
 * → 创建 emitter。异步阶段（boundedElastic）：组装 prompt → 模型流式 → 首行协议解析 →
 * {@code emergency}/{@code delta} → 落库助手消息 → {@code disclaimer}/{@code done} →
 * 写热窗口。失败落 {@code FAILED}，断开落 {@code INTERRUPTED}（均已生成内容保留）。</p>
 */
@Service
public class HealthChatServiceImpl implements HealthChatService {

    private static final Logger LOG = LoggerFactory.getLogger(HealthChatServiceImpl.class);

    private static final int LOCK_TTL_SECONDS = 65;

    private static final Duration WINDOW_TTL = Duration.ofHours(24);

    private static final int WINDOW_ENTRY_MAX_CHARS = 500;

    private static final int TITLE_PREVIEW_CHARS = 20;

    private static final String DEFAULT_TITLE = "新对话";

    private final HealthSessionRepository sessionRepository;

    private final HealthMessageRepository messageRepository;

    private final RedisDialogStore redisDialogStore;

    private final HealthPromptBuilder promptBuilder;

    private final HealthChatClient healthChatClient;

    private final EmergencySymptomService emergencySymptomService;

    private final SseEventWriter sseEventWriter;

    private final AuditEventService auditEventService;

    private final HealthProperties properties;

    /**
     * 构造流式问答实现。
     *
     * @param sessionRepository      会话仓储
     * @param messageRepository      消息仓储
     * @param redisDialogStore       热窗口
     * @param promptBuilder          Prompt 组装器
     * @param healthChatClient       模型出口
     * @param emergencySymptomService 紧急判定
     * @param sseEventWriter         SSE 写出器
     * @param auditEventService      审计
     * @param properties             005 配置
     */
    public HealthChatServiceImpl(HealthSessionRepository sessionRepository,
                                 HealthMessageRepository messageRepository,
                                 RedisDialogStore redisDialogStore,
                                 HealthPromptBuilder promptBuilder,
                                 HealthChatClient healthChatClient,
                                 EmergencySymptomService emergencySymptomService,
                                 SseEventWriter sseEventWriter,
                                 AuditEventService auditEventService,
                                 HealthProperties properties) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.redisDialogStore = redisDialogStore;
        this.promptBuilder = promptBuilder;
        this.healthChatClient = healthChatClient;
        this.emergencySymptomService = emergencySymptomService;
        this.sseEventWriter = sseEventWriter;
        this.auditEventService = auditEventService;
        this.properties = properties;
    }

    @Override
    public SseEmitter chat(long patientId, long sessionId, ChatRequest request,
                           HttpServletResponse response) {
        String content = request.content() == null ? "" : request.content().strip();
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (content.length() > properties.messageMaxChars()) {
            throw new BusinessException(ErrorCode.MESSAGE_TOO_LONG);
        }
        HealthSession session = sessionRepository.findByIdAndPatientId(sessionId, patientId)
                .orElseThrow(() -> {
                    auditEventService.record(AuditEventType.AI_HEALTH_ACCESS_DENIED, "DENIED",
                            patientId, null, null, "sessionId=" + sessionId);
                    return new BusinessException(ErrorCode.SESSION_NOT_FOUND);
                });
        if (session.messageCount() >= properties.sessionMessageLimit()) {
            throw new BusinessException(ErrorCode.SESSION_MESSAGE_LIMIT);
        }

        RedisDialogStore.LockOutcome lock = redisDialogStore.lock(sessionId, LOCK_TTL_SECONDS);
        if (!lock.acquired()) {
            throw new BusinessException(ErrorCode.CHAT_BUSY);
        }
        boolean handedOver = false;
        try {
            // 先读窗口再落库，避免本轮患者消息在重建窗口时重复进入历史
            List<ChatTurn> window = loadWindow(sessionId);
            LocalDateTime now = LocalDateTime.now();
            long patientMessageId = messageRepository.insert(new HealthMessage(0L, sessionId,
                    MessageRole.PATIENT, content, null, false, null, StreamStatus.COMPLETED,
                    null, now));
            updateDefaultTitle(session, content);

            SseEmitter emitter = new SseEmitter(properties.timeoutMillis() + 2000L);
            sseEventWriter.prepare(response);
            StreamState state = new StreamState(patientId, sessionId, patientMessageId, content,
                    emitter, lock.token(), session);
            Mono.fromCallable(() -> promptBuilder.build(patientId, window, content))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(prompt -> runStream(state, prompt),
                            error -> failBeforeStream(state, error));
            handedOver = true;
            return emitter;
        } finally {
            if (!handedOver) {
                redisDialogStore.unlock(sessionId, lock.token());
            }
        }
    }

    private void runStream(StreamState state, PromptResult prompt) {
        try {
            sseEventWriter.accepted(state.emitter, state.patientMessageId, state.sessionId);
            healthChatClient.stream(prompt.command()).subscribe(
                    chunk -> onChunk(state, chunk),
                    error -> onStreamError(state, prompt, error),
                    () -> onStreamComplete(state, prompt));
        } catch (ClientDisconnectedException ex) {
            markInterrupted(state, "accepted send failed");
        } catch (RuntimeException ex) {
            onStreamError(state, prompt, ex);
        }
    }

    private void onChunk(StreamState state, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        if (state.headerParsed) {
            appendDelta(state, chunk);
            return;
        }
        state.header.append(chunk);
        if (state.header.indexOf("\n") < 0
                && state.header.length() < EmergencyHeaderParser.MAX_HEADER_BUFFER) {
            return;
        }
        applyHeader(state);
    }

    private void applyHeader(StreamState state) {
        EmergencyHeaderParser.ParsedAnswer parsed =
                EmergencyHeaderParser.parse(state.header.toString());
        state.headerParsed = true;
        EmergencySymptomService.Evaluation evaluation = emergencySymptomService.evaluate(
                state.sessionId, state.patientText, parsed.signal());
        state.evaluation = evaluation;
        if (evaluation.shouldWarn()) {
            state.warned = true;
            state.ruleCode = evaluation.payload().ruleCode();
            sseEventWriter.emergency(state.emitter, evaluation.payload());
        }
        if (!parsed.body().isEmpty()) {
            appendDelta(state, parsed.body());
        }
    }

    private void appendDelta(StreamState state, String text) {
        state.answer.append(text);
        sseEventWriter.delta(state.emitter, text);
    }

    private void onStreamComplete(StreamState state, PromptResult prompt) {
        long messageId = 0L;
        try {
            if (!state.headerParsed) {
                applyHeader(state);
            }
            String answer = state.answer.toString();
            messageId = messageRepository.insert(new HealthMessage(0L, state.sessionId,
                    MessageRole.ASSISTANT, answer, HealthDisclaimer.TEXT, state.warned,
                    state.ruleCode, StreamStatus.COMPLETED, properties.model(),
                    LocalDateTime.now()));
            state.assistantMessageId = messageId;
            emergencySymptomService.attachMessage(
                    state.evaluation == null ? null : state.evaluation.decisionId(), messageId);

            sseEventWriter.disclaimer(state.emitter);
            sseEventWriter.done(state.emitter, messageId, prompt.contextTruncated(),
                    properties.model());
            persistWindow(state, answer);
            sseEventWriter.complete(state.emitter);
        } catch (ClientDisconnectedException ex) {
            if (messageId > 0L) {
                messageRepository.markStreamStatus(messageId, StreamStatus.INTERRUPTED);
            }
            LOG.info("client disconnected during done, sessionId={}", state.sessionId);
        } catch (RuntimeException ex) {
            LOG.error("stream finalize failed, sessionId={}, reason={}", state.sessionId,
                    ex.getClass().getSimpleName());
            markInterrupted(state, "finalize failed");
        } finally {
            redisDialogStore.unlock(state.sessionId, state.lockToken);
        }
    }

    private void onStreamError(StreamState state, PromptResult prompt, Throwable error) {
        boolean timeout = error instanceof HealthChatException chatException
                && chatException.reason() == HealthChatException.Reason.TIMEOUT;
        LOG.warn("chat stream failed, sessionId={}, reason={}", state.sessionId,
                error.getClass().getSimpleName());
        long messageId = messageRepository.insert(new HealthMessage(0L, state.sessionId,
                MessageRole.ASSISTANT, state.answer.toString(), null, state.warned,
                state.ruleCode, StreamStatus.FAILED, properties.model(), LocalDateTime.now()));
        state.assistantMessageId = messageId;
        emergencySymptomService.attachMessage(
                state.evaluation == null ? null : state.evaluation.decisionId(), messageId);
        auditEventService.record(AuditEventType.AI_HEALTH_CHAT_FAILED, "FAILED", state.patientId,
                null, null, timeout ? "TIMEOUT" : "UPSTREAM_ERROR");
        try {
            sseEventWriter.error(state.emitter, ErrorCode.AI_SERVICE_UNAVAILABLE, true);
            sseEventWriter.complete(state.emitter);
        } catch (ClientDisconnectedException ex) {
            messageRepository.markStreamStatus(messageId, StreamStatus.INTERRUPTED);
        } finally {
            redisDialogStore.unlock(state.sessionId, state.lockToken);
        }
    }

    private void failBeforeStream(StreamState state, Throwable error) {
        LOG.warn("prompt build failed, sessionId={}, reason={}", state.sessionId,
                error.getClass().getSimpleName());
        auditEventService.record(AuditEventType.AI_HEALTH_CHAT_FAILED, "FAILED", state.patientId,
                null, null, "PROMPT_FAILED");
        long messageId = messageRepository.insert(new HealthMessage(0L, state.sessionId,
                MessageRole.ASSISTANT, "", null, false, null, StreamStatus.FAILED,
                properties.model(), LocalDateTime.now()));
        try {
            sseEventWriter.error(state.emitter, ErrorCode.AI_SERVICE_UNAVAILABLE, true);
            sseEventWriter.complete(state.emitter);
        } catch (ClientDisconnectedException ex) {
            messageRepository.markStreamStatus(messageId, StreamStatus.INTERRUPTED);
        } finally {
            redisDialogStore.unlock(state.sessionId, state.lockToken);
        }
    }

    private void markInterrupted(StreamState state, String reason) {
        LOG.info("chat interrupted, sessionId={}, reason={}", state.sessionId, reason);
        if (state.assistantMessageId > 0L) {
            messageRepository.markStreamStatus(state.assistantMessageId, StreamStatus.INTERRUPTED);
        }
        redisDialogStore.unlock(state.sessionId, state.lockToken);
    }

    private void persistWindow(StreamState state, String answer) {
        LocalDateTime now = LocalDateTime.now();
        sessionRepository.touch(state.sessionId, 2, now);
        List<String> entries = List.of(
                "PATIENT|" + truncate(state.patientText),
                "ASSISTANT|" + truncate(answer));
        redisDialogStore.appendWindow(state.sessionId, entries, properties.historyWindow(),
                WINDOW_TTL);
        redisDialogStore.saveMeta(state.sessionId, new DialogMeta(state.patientId,
                state.session.title(), now.toString(), state.session.messageCount() + 2), WINDOW_TTL);
    }

    private List<ChatTurn> loadWindow(long sessionId) {
        List<String> raw = redisDialogStore.window(sessionId);
        if (raw.isEmpty()) {
            List<HealthMessage> recent =
                    messageRepository.findRecentCompleted(sessionId, properties.historyWindow());
            raw = recent.stream().map(this::toWindowEntry).toList();
            redisDialogStore.rebuildWindow(sessionId, raw, properties.historyWindow(), WINDOW_TTL);
        }
        return raw.stream().map(this::toTurn).filter(Objects::nonNull).toList();
    }

    private String toWindowEntry(HealthMessage message) {
        return (message.role() == MessageRole.ASSISTANT ? "ASSISTANT" : "PATIENT")
                + "|" + truncate(message.content());
    }

    private ChatTurn toTurn(String entry) {
        int index = entry.indexOf('|');
        if (index <= 0) {
            return null;
        }
        String role = "ASSISTANT".equals(entry.substring(0, index))
                ? ChatTurn.ROLE_ASSISTANT : ChatTurn.ROLE_USER;
        return new ChatTurn(role, entry.substring(index + 1));
    }

    private void updateDefaultTitle(HealthSession session, String content) {
        if (session.messageCount() != 0 || !DEFAULT_TITLE.equals(session.title())) {
            return;
        }
        String title = content.replaceAll("\\s+", " ").strip();
        if (title.length() > TITLE_PREVIEW_CHARS) {
            title = title.substring(0, TITLE_PREVIEW_CHARS);
        }
        sessionRepository.rename(session.id(), session.patientId(), title, session.version());
    }

    private static String truncate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= WINDOW_ENTRY_MAX_CHARS
                ? content
                : content.substring(0, WINDOW_ENTRY_MAX_CHARS);
    }

    /** 单轮流式处理状态（只在单线程回调链内使用）。 */
    private static final class StreamState {

        private final long patientId;

        private final long sessionId;

        private final long patientMessageId;

        private final String patientText;

        private final SseEmitter emitter;

        private final String lockToken;

        private final HealthSession session;

        private final StringBuilder header = new StringBuilder();

        private final StringBuilder answer = new StringBuilder();

        private boolean headerParsed;

        private boolean warned;

        private String ruleCode;

        private EmergencySymptomService.Evaluation evaluation;

        private long assistantMessageId;

        private StreamState(long patientId, long sessionId, long patientMessageId,
                            String patientText, SseEmitter emitter, String lockToken,
                            HealthSession session) {
            this.patientId = patientId;
            this.sessionId = sessionId;
            this.patientMessageId = patientMessageId;
            this.patientText = patientText;
            this.emitter = emitter;
            this.lockToken = lockToken;
            this.session = session;
        }
    }
}
