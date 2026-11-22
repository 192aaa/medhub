package com.sdas.server.doctor.service.impl;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.doctor.dto.AiDecisionResponse;
import com.sdas.server.doctor.entity.AiDecisionSession;
import com.sdas.server.doctor.model.TreatmentDecisionStatus;
import com.sdas.server.doctor.repository.AiDecisionSessionRepository;
import com.sdas.server.doctor.service.AiDecisionClient;
import com.sdas.server.doctor.service.AiDecisionResult;
import com.sdas.server.doctor.service.AiDecisionService;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 辅助决策实现：调用客户端 → 组装建议 + 固定声明，写会话留痕；
 * 超时/异常降级为 {@code TIMEOUT_FALLBACK}（HTTP 200），不阻塞接诊开方。
 */
@Service
public class AiDecisionServiceImpl implements AiDecisionService {

    /** 固定声明（contracts/doctor-consultation-api.md 端点 9）。 */
    public static final String DISCLAIMER = "本内容为 AI 辅助参考，不作为诊断结论。";

    private final ConsultationRepository consultationRepository;

    private final AiDecisionClient aiDecisionClient;

    private final AiDecisionSessionRepository sessionRepository;

    private final AuditEventService auditEventService;

    /**
     * 构造辅助决策服务。
     *
     * @param consultationRepository 问诊单仓储
     * @param aiDecisionClient       AI 客户端
     * @param sessionRepository      会话仓储
     * @param auditEventService      审计
     */
    public AiDecisionServiceImpl(ConsultationRepository consultationRepository,
                                 AiDecisionClient aiDecisionClient,
                                 AiDecisionSessionRepository sessionRepository,
                                 AuditEventService auditEventService) {
        this.consultationRepository = consultationRepository;
        this.aiDecisionClient = aiDecisionClient;
        this.sessionRepository = sessionRepository;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional
    public AiDecisionResponse decide(long doctorId, long consultationId, String query) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        if (!consultation.doctorId().equals(doctorId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_FORBIDDEN);
        }
        if (consultation.status() != ConsultationStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_CONSULTATION_STATUS);
        }
        String prompt = buildPrompt(consultation, query);
        long start = System.currentTimeMillis();
        AiDecisionResult result = aiDecisionClient.decide(prompt);
        int elapsedMs = (int) (System.currentTimeMillis() - start);
        TreatmentDecisionStatus status = result.success()
                ? TreatmentDecisionStatus.SUCCESS : TreatmentDecisionStatus.TIMEOUT_FALLBACK;
        String output = String.join("\n", result.suggestions());
        long sessionId = sessionRepository.insert(new AiDecisionSession(null, consultationId,
                doctorId, inputSummary(consultation), output, result.modelUsed(), status,
                elapsedMs, null));
        auditEventService.record(AuditEventType.AI_DECISION_REQUESTED, "SUCCESS",
                consultation.patientId(), null, null, null);
        return new AiDecisionResponse(sessionId, result.suggestions(), DISCLAIMER,
                result.modelUsed(), status.name());
    }

    private String buildPrompt(Consultation consultation, String query) {
        StringBuilder builder = new StringBuilder();
        builder.append("主诉：").append(consultation.complaint());
        if (consultation.description() != null && !consultation.description().isBlank()) {
            builder.append("；描述：").append(consultation.description());
        }
        if (query != null && !query.isBlank()) {
            builder.append("；医生提问：").append(query);
        }
        return builder.toString();
    }

    private String inputSummary(Consultation consultation) {
        String summary = consultation.summary() != null ? consultation.summary() : consultation.complaint();
        return summary.length() > 2000 ? summary.substring(0, 2000) : summary;
    }
}
