package com.sdas.server.doctor.support;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.repository.ConsultationImageRepository;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.TreatmentDecisionProperties;
import com.sdas.server.doctor.entity.ConsultationMessage;
import com.sdas.server.doctor.model.MessageType;
import com.sdas.server.doctor.repository.ConsultationMessageRepository;
import com.sdas.server.healthconsultation.support.KnowledgeHit;
import com.sdas.server.healthconsultation.support.KnowledgeRetriever;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 治疗决策上下文组装（plan.md M2 / R-06）。
 *
 * <p>只读聚合问诊单主诉与描述、最近对话、图片数量与知识 Top-K；
 * 对话按「最近优先」裁剪字符预算后反转为正序，{@code evidenceLimited} 由服务端判定。
 * 本类不写日志、不落库，避免病情明文外泄。</p>
 */
@Component
public class DecisionContextAssembler {

    /** 主诉/描述字段截断长度。 */
    private static final int FIELD_MAX = 500;

    /** 参与知识检索的最近患者消息条数。 */
    private static final int KNOWLEDGE_MESSAGES = 3;

    private final ConsultationRepository consultationRepository;

    private final ConsultationMessageRepository consultationMessageRepository;

    private final ConsultationImageRepository consultationImageRepository;

    private final KnowledgeRetriever knowledgeRetriever;

    private final TreatmentDecisionProperties properties;

    /**
     * 构造上下文组装器。
     *
     * @param consultationRepository        问诊单只读仓储
     * @param consultationMessageRepository 对话只读仓储
     * @param consultationImageRepository   图片只读仓储
     * @param knowledgeRetriever            005 知识检索器
     * @param properties                    006 配置
     */
    public DecisionContextAssembler(ConsultationRepository consultationRepository,
                                    ConsultationMessageRepository consultationMessageRepository,
                                    ConsultationImageRepository consultationImageRepository,
                                    KnowledgeRetriever knowledgeRetriever,
                                    TreatmentDecisionProperties properties) {
        this.consultationRepository = consultationRepository;
        this.consultationMessageRepository = consultationMessageRepository;
        this.consultationImageRepository = consultationImageRepository;
        this.knowledgeRetriever = knowledgeRetriever;
        this.properties = properties;
    }

    /**
     * 组装上下文。
     *
     * @param consultationId 问诊单主键
     * @param departmentId   科室主键（用于知识同科室优先排序）
     * @return 上下文（不落库）
     */
    public DecisionContext assemble(long consultationId, long departmentId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        String chiefComplaint = truncate(consultation.complaint(), FIELD_MAX);
        String description = truncate(consultation.description(), FIELD_MAX);
        List<ChatTurn> dialog = window(
                consultationMessageRepository.findByConsultationId(consultationId));
        int imageCount = consultationImageRepository.findByConsultationId(consultationId).size();
        boolean evidenceLimited = dialog.size() < 2;
        List<KnowledgeHit> knowledge = retrieveKnowledge(chiefComplaint, description, dialog,
                departmentId);
        return new DecisionContext(chiefComplaint, description, dialog, imageCount,
                evidenceLimited, knowledge);
    }

    private List<KnowledgeHit> retrieveKnowledge(String chiefComplaint, String description,
                                                 List<ChatTurn> dialog, long departmentId) {
        String query = query(chiefComplaint, description, dialog);
        if (query.isBlank()) {
            return List.of();
        }
        List<KnowledgeHit> hits = knowledgeRetriever.retrieve(query, properties.knowledgeTopK());
        return reorder(hits, departmentId);
    }

    private static String query(String chiefComplaint, String description, List<ChatTurn> dialog) {
        StringBuilder builder = new StringBuilder();
        builder.append(chiefComplaint == null ? "" : chiefComplaint).append(' ')
                .append(description == null ? "" : description);
        int patientTurns = 0;
        for (int i = dialog.size() - 1; i >= 0 && patientTurns < KNOWLEDGE_MESSAGES; i--) {
            ChatTurn turn = dialog.get(i);
            if ("user".equals(turn.role())) {
                builder.append(' ').append(turn.content());
                patientTurns++;
            }
        }
        return builder.toString().trim();
    }

    private static List<KnowledgeHit> reorder(List<KnowledgeHit> hits, long departmentId) {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<KnowledgeHit> ordered = new ArrayList<>(hits.size());
        for (KnowledgeHit hit : hits) {
            if (hit.departmentId() == departmentId) {
                ordered.add(hit);
            }
        }
        for (KnowledgeHit hit : hits) {
            if (hit.departmentId() != departmentId) {
                ordered.add(hit);
            }
        }
        return List.copyOf(ordered);
    }

    private List<ChatTurn> window(List<ConsultationMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, messages.size() - properties.dialogMaxMessages());
        List<ChatTurn> reversed = new ArrayList<>();
        int used = 0;
        for (int i = messages.size() - 1; i >= from; i--) {
            ConsultationMessage message = messages.get(i);
            String content = message.content() == null ? "" : message.content();
            if (!reversed.isEmpty() && used + content.length() > properties.dialogMaxChars()) {
                break;
            }
            reversed.add(new ChatTurn(role(message.senderType()),
                    truncate(content, properties.dialogMaxChars())));
            used += content.length();
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static String role(MessageType senderType) {
        return senderType == MessageType.DOCTOR ? "assistant" : "user";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
