package com.sdas.server.healthconsultation.service.impl;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.PageResult;
import com.sdas.server.healthconsultation.dto.ClearAllResponse;
import com.sdas.server.healthconsultation.dto.MessageItem;
import com.sdas.server.healthconsultation.dto.SessionCreateResponse;
import com.sdas.server.healthconsultation.dto.SessionDeleteResponse;
import com.sdas.server.healthconsultation.dto.SessionRenameResponse;
import com.sdas.server.healthconsultation.dto.SessionSummary;
import com.sdas.server.healthconsultation.entity.HealthMessage;
import com.sdas.server.healthconsultation.entity.HealthSession;
import com.sdas.server.healthconsultation.repository.HealthMessageRepository;
import com.sdas.server.healthconsultation.repository.HealthSessionRepository;
import com.sdas.server.healthconsultation.service.HealthSessionService;
import com.sdas.server.healthconsultation.support.RedisDialogStore;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 会话管理实现。
 *
 * <p>契约：{@code contracts/ai-health-consultation-api.md} 端点 1–6。
 * 所有查询强制携带 {@code patientId}；清空历史只记数量、不记内容（FR-014/FR-017）。</p>
 */
@Service
public class HealthSessionServiceImpl implements HealthSessionService {

    private static final String DEFAULT_TITLE = "新对话";

    private static final int TITLE_MAX_CHARS = 64;

    private static final int PREVIEW_MAX_CHARS = 40;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private final HealthSessionRepository sessionRepository;

    private final HealthMessageRepository messageRepository;

    private final RedisDialogStore redisDialogStore;

    private final AuditEventService auditEventService;

    /**
     * 构造会话管理实现。
     *
     * @param sessionRepository 会话仓储
     * @param messageRepository 消息仓储
     * @param redisDialogStore  Redis 热窗口
     * @param auditEventService 审计服务
     */
    public HealthSessionServiceImpl(HealthSessionRepository sessionRepository,
                                    HealthMessageRepository messageRepository,
                                    RedisDialogStore redisDialogStore,
                                    AuditEventService auditEventService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.redisDialogStore = redisDialogStore;
        this.auditEventService = auditEventService;
    }

    @Override
    public SessionCreateResponse create(long patientId) {
        LocalDateTime now = LocalDateTime.now();
        long sessionId = sessionRepository.insert(patientId, DEFAULT_TITLE, now);
        return new SessionCreateResponse(sessionId, DEFAULT_TITLE, now);
    }

    @Override
    public PageResult<SessionSummary> list(long patientId, int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = sessionRepository.countByPatientId(patientId);
        List<SessionSummary> items = sessionRepository
                .findByPatientId(patientId, (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream()
                .map(session -> new SessionSummary(session.id(), session.title(),
                        session.lastActiveAt(), session.messageCount(),
                        messageRepository.findLastBySessionId(session.id())
                                .map(HealthMessage::content)
                                .map(this::preview)
                                .orElse(null)))
                .toList();
        return new PageResult<>(items, total, normalizedPage, normalizedSize);
    }

    @Override
    public PageResult<MessageItem> messages(long patientId, long sessionId, int page,
                                            int pageSize) {
        requireOwned(patientId, sessionId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = pageSize <= 0 ? MAX_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = messageRepository.countBySessionId(sessionId);
        List<MessageItem> items = messageRepository
                .findBySessionId(sessionId, (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream()
                .map(message -> new MessageItem(message.id(), message.role(), message.content(),
                        message.disclaimer(), message.emergency(), message.streamStatus(),
                        message.createdAt()))
                .toList();
        return new PageResult<>(items, total, normalizedPage, normalizedSize);
    }

    @Override
    public SessionRenameResponse rename(long patientId, long sessionId, String title) {
        HealthSession session = requireOwned(patientId, sessionId);
        String normalized = title == null ? "" : title.strip();
        if (normalized.isEmpty() || normalized.length() > TITLE_MAX_CHARS) {
            throw new BusinessException(ErrorCode.SESSION_TITLE_INVALID);
        }
        int updated = sessionRepository.rename(sessionId, patientId, normalized, session.version());
        if (updated == 0) {
            // 并发修改后版本已变：重读一次再尝试，仍失败视为非法标题
            HealthSession latest = requireOwned(patientId, sessionId);
            if (sessionRepository.rename(sessionId, patientId, normalized, latest.version()) == 0) {
                throw new BusinessException(ErrorCode.SESSION_TITLE_INVALID);
            }
        }
        return new SessionRenameResponse(sessionId, normalized);
    }

    @Override
    public SessionDeleteResponse delete(long patientId, long sessionId) {
        requireOwned(patientId, sessionId);
        sessionRepository.markDeleted(sessionId, patientId);
        messageRepository.deleteBySessionId(sessionId);
        redisDialogStore.deleteAll(sessionId);
        return new SessionDeleteResponse(sessionId, true);
    }

    @Override
    @Transactional
    public ClearAllResponse clearAll(long patientId) {
        List<Long> sessionIds = sessionRepository
                .findByPatientId(patientId, 0, Integer.MAX_VALUE).stream()
                .map(HealthSession::id)
                .toList();
        int deletedCount = sessionRepository.markAllDeleted(patientId);
        messageRepository.deleteByPatientId(patientId);

        Runnable clearCache = () -> sessionIds.forEach(redisDialogStore::deleteAll);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            clearCache.run();
                        }
                    });
        } else {
            clearCache.run();
        }

        auditEventService.record(AuditEventType.AI_HEALTH_HISTORY_CLEARED, "SUCCESS", patientId,
                null, null, "deletedCount=" + deletedCount);
        return new ClearAllResponse(deletedCount);
    }

    /**
     * 校验会话归属（越权与不存在同码）。
     *
     * @param patientId 患者主键
     * @param sessionId 会话主键
     * @return 会话
     */
    HealthSession requireOwned(long patientId, long sessionId) {
        return sessionRepository.findByIdAndPatientId(sessionId, patientId)
                .orElseThrow(() -> {
                    auditEventService.record(AuditEventType.AI_HEALTH_ACCESS_DENIED, "DENIED",
                            patientId, null, null, "sessionId=" + sessionId);
                    return new BusinessException(ErrorCode.SESSION_NOT_FOUND);
                });
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        String stripped = content.strip();
        return stripped.length() <= PREVIEW_MAX_CHARS
                ? stripped
                : stripped.substring(0, PREVIEW_MAX_CHARS) + "…";
    }
}
