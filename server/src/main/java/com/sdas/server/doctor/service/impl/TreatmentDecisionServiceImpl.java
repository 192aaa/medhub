package com.sdas.server.doctor.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.TreatmentDecisionProperties;
import com.sdas.server.doctor.dto.CaseSummaryView;
import com.sdas.server.doctor.dto.DiagnosisSuggestionView;
import com.sdas.server.doctor.dto.MedicationDraftView;
import com.sdas.server.doctor.dto.TreatmentAdoptionResponse;
import com.sdas.server.doctor.dto.TreatmentDecisionResponse;
import com.sdas.server.doctor.entity.AiDecisionSession;
import com.sdas.server.doctor.entity.ConsultationMessage;
import com.sdas.server.doctor.entity.DrugCatalog;
import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.model.TreatmentDecisionStatus;
import com.sdas.server.doctor.model.TreatmentTriggerType;
import com.sdas.server.doctor.repository.AiDecisionSessionRepository;
import com.sdas.server.doctor.repository.ConsultationMessageRepository;
import com.sdas.server.doctor.repository.DrugCatalogRepository;
import com.sdas.server.doctor.repository.PrescriptionRepository;
import com.sdas.server.doctor.service.TreatmentDecisionClient;
import com.sdas.server.doctor.service.TreatmentDecisionCommand;
import com.sdas.server.doctor.service.TreatmentDecisionResult;
import com.sdas.server.doctor.service.TreatmentDecisionService;
import com.sdas.server.doctor.support.DecisionContext;
import com.sdas.server.doctor.support.DecisionContextAssembler;
import com.sdas.server.doctor.support.DecisionPayloadParser;
import com.sdas.server.doctor.support.DecisionPromptBuilder;
import com.sdas.server.doctor.support.DecisionResult;
import com.sdas.server.doctor.support.DiagnosisSuggestion;
import com.sdas.server.doctor.support.MapResult;
import com.sdas.server.doctor.support.MedicationDraft;
import com.sdas.server.doctor.support.MedicationMapper;
import com.sdas.server.doctor.support.ParseResult;
import com.sdas.server.doctor.support.ParsedDecision;
import com.sdas.server.doctor.support.RawMedication;
import com.sdas.server.doctor.support.TreatmentDecisionDisclaimer;
import com.sdas.server.doctor.support.TreatmentLimitStore;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 治疗决策辅助编排实现（plan.md 一次开方完整时序第 4–13 步）。
 *
 * <p>顺序固定为：校验 → 幂等 → 频控 → 抢锁 → 组装 → 调用 → 解析 → 映射 → 落痕 → 释放锁。
 * 不加事务包裹：AI 调用最长 15s，不能占用数据库连接；落痕为单条 INSERT。</p>
 *
 * <p>安全：{@code input_summary} 与 {@code result_json} 均脱敏截断；审计只记 ID 与状态；
 * 日志不打印提示词与模型输出。</p>
 */
@Service
public class TreatmentDecisionServiceImpl implements TreatmentDecisionService {

    /** 留痕 input_summary 上限（列宽 2000）。 */
    private static final int MAX_SUMMARY_CHARS = 2000;

    /** 留痕 output 上限（列宽 2000，兼容 003 端点 9）。 */
    private static final int MAX_OUTPUT_CHARS = 2000;

    private static final int MAX_FIELD_CHARS = 500;

    private static final int MAX_DIALOG_CHARS = 1500;

    private static final int MAX_ADOPTION_ITEMS = 20;

    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");

    private static final Pattern PHONE = Pattern.compile("1\\d{10}");

    private final ConsultationRepository consultationRepository;

    private final PrescriptionRepository prescriptionRepository;

    private final ConsultationMessageRepository consultationMessageRepository;

    private final AiDecisionSessionRepository sessionRepository;

    private final DrugCatalogRepository drugCatalogRepository;

    private final TreatmentDecisionClient client;

    private final DecisionContextAssembler contextAssembler;

    private final DecisionPromptBuilder promptBuilder;

    private final DecisionPayloadParser payloadParser;

    private final MedicationMapper medicationMapper;

    private final TreatmentLimitStore limitStore;

    private final AuditEventService auditEventService;

    private final TreatmentDecisionProperties properties;

    private final ObjectMapper objectMapper;

    /**
     * 构造编排服务。
     *
     * @param consultationRepository        问诊单仓储
     * @param prescriptionRepository        处方仓储（判断是否已开方）
     * @param consultationMessageRepository 对话仓储（对话指纹）
     * @param sessionRepository             留痕仓储
     * @param drugCatalogRepository         药品目录仓储
     * @param client                        模型客户端
     * @param contextAssembler              上下文组装器
     * @param promptBuilder                 提示词构造器
     * @param payloadParser                 输出解析器
     * @param medicationMapper              药品映射器
     * @param limitStore                    锁与频控存储
     * @param auditEventService             审计
     * @param properties                    006 配置
     * @param objectMapper                  JSON 序列化
     */
    public TreatmentDecisionServiceImpl(ConsultationRepository consultationRepository,
                                        PrescriptionRepository prescriptionRepository,
                                        ConsultationMessageRepository consultationMessageRepository,
                                        AiDecisionSessionRepository sessionRepository,
                                        DrugCatalogRepository drugCatalogRepository,
                                        TreatmentDecisionClient client,
                                        DecisionContextAssembler contextAssembler,
                                        DecisionPromptBuilder promptBuilder,
                                        DecisionPayloadParser payloadParser,
                                        MedicationMapper medicationMapper,
                                        TreatmentLimitStore limitStore,
                                        AuditEventService auditEventService,
                                        TreatmentDecisionProperties properties,
                                        ObjectMapper objectMapper) {
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.consultationMessageRepository = consultationMessageRepository;
        this.sessionRepository = sessionRepository;
        this.drugCatalogRepository = drugCatalogRepository;
        this.client = client;
        this.contextAssembler = contextAssembler;
        this.promptBuilder = promptBuilder;
        this.payloadParser = payloadParser;
        this.medicationMapper = medicationMapper;
        this.limitStore = limitStore;
        this.auditEventService = auditEventService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public TreatmentDecisionResponse generate(long doctorId, long consultationId,
                                              TreatmentTriggerType trigger) {
        Consultation consultation = requireInProgress(doctorId, consultationId);
        String dialogVersion = dialogVersion(consultationId);
        if (trigger == TreatmentTriggerType.AUTO) {
            TreatmentDecisionResponse reusable = reuse(doctorId, consultationId, dialogVersion);
            if (reusable != null) {
                return reusable;
            }
        } else {
            limitStore.checkRegenerate(consultationId);
        }
        TreatmentLimitStore.LockOutcome lock = limitStore.lock(consultationId);
        if (!lock.acquired()) {
            throw new BusinessException(ErrorCode.TREATMENT_DECISION_BUSY);
        }
        try {
            return generateWithLock(consultation, dialogVersion, trigger);
        } finally {
            limitStore.unlock(consultationId, lock.token());
        }
    }

    @Override
    public TreatmentDecisionResponse latest(long doctorId, long consultationId) {
        requireOwner(doctorId, consultationId);
        AiDecisionSession session = sessionRepository
                .findLatestByConsultationIdAndDoctorId(consultationId, doctorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREATMENT_DECISION_NOT_FOUND));
        int elapsedMs = session.elapsedMs() == null ? 0 : session.elapsedMs();
        return response(session, false, elapsedMs);
    }

    @Override
    public TreatmentAdoptionResponse markAdopted(long doctorId, long consultationId,
                                                 Integer appliedItemCount) {
        Consultation consultation = requireOwner(doctorId, consultationId);
        if (appliedItemCount == null || appliedItemCount < 0
                || appliedItemCount > MAX_ADOPTION_ITEMS) {
            throw new BusinessException(ErrorCode.INVALID_TREATMENT_ADOPTION);
        }
        AiDecisionSession session = sessionRepository
                .findLatestByConsultationIdAndDoctorId(consultationId, doctorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREATMENT_DECISION_NOT_FOUND));
        sessionRepository.markAdopted(session.id(), appliedItemCount, LocalDateTime.now());
        auditEventService.record(AuditEventType.AI_TREATMENT_DECISION_ADOPTED, "SUCCESS",
                consultation.patientId(), null, null, String.valueOf(appliedItemCount));
        return new TreatmentAdoptionResponse(session.id(), appliedItemCount);
    }

    private TreatmentDecisionResponse reuse(long doctorId, long consultationId,
                                            String dialogVersion) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(properties.reuseWindowMinutes());
        return sessionRepository.findReusable(consultationId, doctorId, dialogVersion, since)
                .map(session -> response(session, true, 0))
                .orElse(null);
    }

    private TreatmentDecisionResponse generateWithLock(Consultation consultation,
                                                       String dialogVersion,
                                                       TreatmentTriggerType trigger) {
        long start = System.currentTimeMillis();
        DecisionContext context = contextAssembler
                .assemble(consultation.id(), consultation.departmentId());
        TreatmentDecisionCommand command = new TreatmentDecisionCommand(
                promptBuilder.systemPrompt(), promptBuilder.build(context),
                properties.maxOutputTokens(), properties.model());
        TreatmentDecisionResult result = client.decide(command);
        int elapsedMs = (int) (System.currentTimeMillis() - start);
        if (!result.success()) {
            return completeDegraded(consultation, dialogVersion, trigger, result.reason(),
                    elapsedMs, context);
        }
        ParseResult parseResult = payloadParser.parse(result.rawJson());
        if (!parseResult.success()) {
            return completeDegraded(consultation, dialogVersion, trigger, parseResult.reason(),
                    elapsedMs, context);
        }
        return completeSuccess(consultation, dialogVersion, trigger, result, parseResult.decision(),
                elapsedMs, context);
    }

    private TreatmentDecisionResponse completeSuccess(Consultation consultation,
                                                      String dialogVersion,
                                                      TreatmentTriggerType trigger,
                                                      TreatmentDecisionResult result,
                                                      ParsedDecision parsed, int elapsedMs,
                                                      DecisionContext context) {
        MapResult mapResult = medicationMapper.map(parsed.medications(), loadCatalog(parsed));
        LocalDateTime generatedAt = LocalDateTime.now();
        CaseSummaryView summary = new CaseSummaryView(
                parsed.summary().chiefComplaint(), parsed.summary().presentIllness(),
                parsed.summary().dialogHighlights(), parsed.summary().riskAlerts(),
                context.evidenceLimited(), generatedAt, result.modelUsed());
        List<DiagnosisSuggestionView> suggestions = parsed.suggestions().stream()
                .map(TreatmentDecisionServiceImpl::suggestionView).toList();
        List<MedicationDraftView> drafts = mapResult.drafts().stream()
                .map(TreatmentDecisionServiceImpl::draftView).toList();
        DecisionResult decisionResult = new DecisionResult(summary, suggestions, drafts,
                mapResult.ignoredMedications(), DegradedReason.NONE);
        long decisionId = insertSession(consultation, dialogVersion, trigger,
                TreatmentDecisionStatus.SUCCESS, DegradedReason.NONE, elapsedMs, generatedAt,
                result.modelUsed(), inputSummary(context), outputText(suggestions),
                writeJson(decisionResult));
        auditEventService.record(AuditEventType.AI_TREATMENT_DECISION_REQUESTED, "SUCCESS",
                consultation.patientId(), null, null, null);
        return new TreatmentDecisionResponse(decisionId, consultation.id(),
                TreatmentDecisionStatus.SUCCESS, trigger, false, summary, suggestions, drafts,
                mapResult.ignoredMedications(), TreatmentDecisionDisclaimer.TEXT,
                DegradedReason.NONE, elapsedMs, generatedAt);
    }

    private TreatmentDecisionResponse completeDegraded(Consultation consultation,
                                                       String dialogVersion,
                                                       TreatmentTriggerType trigger,
                                                       DegradedReason reason, int elapsedMs,
                                                       DecisionContext context) {
        LocalDateTime generatedAt = LocalDateTime.now();
        DecisionResult decisionResult = DecisionResult.degraded(reason);
        long decisionId = insertSession(consultation, dialogVersion, trigger, statusOf(reason),
                reason, elapsedMs, generatedAt, null, inputSummary(context), null,
                writeJson(decisionResult));
        auditEventService.record(AuditEventType.AI_TREATMENT_DECISION_REQUESTED, "FAILURE",
                consultation.patientId(), null, null, reason.name());
        auditEventService.record(AuditEventType.AI_TREATMENT_DECISION_DEGRADED, "FAILURE",
                consultation.patientId(), null, null, reason.name());
        return new TreatmentDecisionResponse(decisionId, consultation.id(), statusOf(reason),
                trigger, false, null, List.of(), List.of(), List.of(),
                TreatmentDecisionDisclaimer.TEXT, reason, elapsedMs, generatedAt);
    }

    private long insertSession(Consultation consultation, String dialogVersion,
                               TreatmentTriggerType trigger, TreatmentDecisionStatus status,
                               DegradedReason reason, int elapsedMs, LocalDateTime generatedAt,
                               String modelUsed, String inputSummary, String output,
                               String resultJson) {
        return sessionRepository.insert(new AiDecisionSession(null, consultation.id(),
                consultation.doctorId(), inputSummary, output, modelUsed, status, elapsedMs,
                generatedAt, trigger, dialogVersion, resultJson, reason, null, null));
    }

    private List<DrugCatalog> loadCatalog(ParsedDecision parsed) {
        List<String> names = parsed.medications().stream()
                .map(RawMedication::drugName).distinct().toList();
        return drugCatalogRepository.findEnabledByNames(names);
    }

    private TreatmentDecisionResponse response(AiDecisionSession session, boolean reused,
                                               int elapsedMs) {
        DecisionResult stored = readResult(session.resultJson(), session.degradedReason());
        return new TreatmentDecisionResponse(session.id(), session.consultationId(),
                session.status(), session.triggerType() == null
                        ? TreatmentTriggerType.AUTO : session.triggerType(),
                reused, stored.summary(), stored.suggestions(), stored.medicationDrafts(),
                stored.ignoredMedications(), TreatmentDecisionDisclaimer.TEXT,
                stored.degradedReason(), elapsedMs, session.createdAt());
    }

    private Consultation requireOwner(long doctorId, long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        if (!consultation.doctorId().equals(doctorId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_FORBIDDEN);
        }
        return consultation;
    }

    private Consultation requireInProgress(long doctorId, long consultationId) {
        Consultation consultation = requireOwner(doctorId, consultationId);
        if (consultation.status() != ConsultationStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_CONSULTATION_STATUS);
        }
        if (prescriptionRepository.findByConsultationId(consultationId).isPresent()) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_ALREADY_EXISTS);
        }
        return consultation;
    }

    private String dialogVersion(long consultationId) {
        List<ConsultationMessage> messages =
                consultationMessageRepository.findByConsultationId(consultationId);
        long lastMessageId = messages.isEmpty() ? 0L : messages.get(messages.size() - 1).id();
        boolean hasPrescription = prescriptionRepository
                .findByConsultationId(consultationId).isPresent();
        String raw = lastMessageId + ":" + messages.size() + ":" + hasPrescription;
        return sha256(raw).substring(0, 16);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static TreatmentDecisionStatus statusOf(DegradedReason reason) {
        if (reason == DegradedReason.TIMEOUT) {
            return TreatmentDecisionStatus.TIMEOUT_FALLBACK;
        }
        if (reason == DegradedReason.UPSTREAM_ERROR) {
            return TreatmentDecisionStatus.ERROR_FALLBACK;
        }
        return TreatmentDecisionStatus.EMPTY_FALLBACK;
    }

    private static DiagnosisSuggestionView suggestionView(DiagnosisSuggestion suggestion) {
        return new DiagnosisSuggestionView(suggestion.diagnosis(), suggestion.likelihood(),
                suggestion.basis(), suggestion.workup(), suggestion.riskAlert());
    }

    private static MedicationDraftView draftView(MedicationDraft draft) {
        return new MedicationDraftView(draft.drugId(), draft.drugName(), draft.spec(),
                draft.dosage(), draft.caution(), draft.needsCompletion());
    }

    private static String inputSummary(DecisionContext context) {
        String dialog = context.dialog().stream()
                .map(turn -> ("user".equals(turn.role()) ? "患者：" : "医生：") + turn.content())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        String summary = "主诉：" + truncate(sanitize(context.chiefComplaint()), MAX_FIELD_CHARS)
                + "；描述：" + truncate(sanitize(context.description()), MAX_FIELD_CHARS)
                + "；对话：" + truncate(sanitize(dialog), MAX_DIALOG_CHARS);
        return truncate(summary, MAX_SUMMARY_CHARS);
    }

    private static String outputText(List<DiagnosisSuggestionView> suggestions) {
        String joined = suggestions.stream().map(DiagnosisSuggestionView::diagnosis)
                .reduce((left, right) -> left + "；" + right).orElse("");
        return truncate(joined, MAX_OUTPUT_CHARS);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return PHONE.matcher(ID_CARD.matcher(value).replaceAll("*")).replaceAll("*");
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String writeJson(DecisionResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            return "{\"degradedReason\":\"PARSE_FAILED\"}";
        }
    }

    private DecisionResult readResult(String resultJson, DegradedReason reason) {
        if (resultJson == null || resultJson.isBlank()) {
            return DecisionResult.degraded(reason == null ? DegradedReason.NONE : reason);
        }
        try {
            return objectMapper.readValue(resultJson, DecisionResult.class);
        } catch (JsonProcessingException exception) {
            return DecisionResult.degraded(DegradedReason.PARSE_FAILED);
        }
    }
}
