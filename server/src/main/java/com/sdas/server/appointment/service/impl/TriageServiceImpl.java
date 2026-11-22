package com.sdas.server.appointment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.appointment.dto.TriageDepartment;
import com.sdas.server.appointment.dto.TriageResponse;
import com.sdas.server.appointment.entity.AiTriageSession;
import com.sdas.server.appointment.entity.Department;
import com.sdas.server.appointment.model.TriageResultStatus;
import com.sdas.server.appointment.repository.AiTriageSessionRepository;
import com.sdas.server.appointment.repository.DepartmentDiseaseKbRepository;
import com.sdas.server.appointment.repository.DepartmentRepository;
import com.sdas.server.appointment.service.TriageAiResult;
import com.sdas.server.appointment.service.TriageClient;
import com.sdas.server.appointment.service.TriageService;
import com.sdas.server.config.TriageProperties;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** AI 智能导诊实现（端点 4）。超时/异常降级为全量科室列表，HTTP 200。 */
@Service
public class TriageServiceImpl implements TriageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TriageServiceImpl.class);

    private static final String KB_VERSION = "PRESET";

    private final TriageClient triageClient;

    private final DepartmentRepository departmentRepository;

    private final DepartmentDiseaseKbRepository kbRepository;

    private final AiTriageSessionRepository sessionRepository;

    private final AuditEventService auditEventService;

    private final TriageProperties triageProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造导诊服务。
     *
     * @param triageClient          AI 客户端
     * @param departmentRepository  科室仓储
     * @param kbRepository         知识库仓储
     * @param sessionRepository    会话仓储
     * @param auditEventService    审计
     * @param triageProperties     导诊配置
     */
    public TriageServiceImpl(TriageClient triageClient, DepartmentRepository departmentRepository,
                           DepartmentDiseaseKbRepository kbRepository,
                           AiTriageSessionRepository sessionRepository,
                           AuditEventService auditEventService, TriageProperties triageProperties) {
        this.triageClient = triageClient;
        this.departmentRepository = departmentRepository;
        this.kbRepository = kbRepository;
        this.sessionRepository = sessionRepository;
        this.auditEventService = auditEventService;
        this.triageProperties = triageProperties;
    }

    @Override
    public TriageResponse triage(Long patientId, String complaint) {
        long start = System.currentTimeMillis();
        TriageAiResult result = triageClient.recommend(complaint);
        List<Department> recommended = resolveDepartments(result, complaint);

        TriageResultStatus status;
        List<Department> recommendedList;
        List<TriageDepartment> fallback;
        if (recommended.isEmpty()) {
            status = TriageResultStatus.TIMEOUT_FALLBACK;
            recommendedList = List.of();
            fallback = toTriageDepartments(departmentRepository.findAll());
            auditEventService.record(AuditEventType.TRIAGE_TIMEOUT_FALLBACK, "SUCCESS", patientId,
                    null, null, null);
        } else {
            status = TriageResultStatus.SUCCESS;
            recommendedList = recommended;
            fallback = List.of();
            auditEventService.record(AuditEventType.TRIAGE_RECOMMENDED, "SUCCESS", patientId, null,
                    null, null);
        }
        long elapsed = System.currentTimeMillis() - start;

        List<Long> recommendedIds = recommendedList.stream().map(Department::id).toList();
        AiTriageSession session = new AiTriageSession(null, patientId, complaint,
                toJson(recommendedIds), triageProperties.model(), elapsed, status, KB_VERSION, null,
                null);
        long sessionId = sessionRepository.insert(session);

        return new TriageResponse(sessionId, status.name(), recommendedIds,
                toTriageDepartments(recommendedList), fallback, triageProperties.model(), elapsed);
    }

    private List<Department> resolveDepartments(TriageAiResult result, String complaint) {
        if (!result.success() || result.departmentNames().isEmpty()) {
            return List.of();
        }
        List<Department> all = departmentRepository.findAll();
        List<Department> byName = all.stream()
                .filter(d -> result.departmentNames().stream()
                        .anyMatch(n -> n.equals(d.name()) || n.contains(d.name())
                                || d.name().contains(n)))
                .toList();
        if (!byName.isEmpty()) {
            return byName;
        }
        List<Long> kbIds = kbRepository.findDepartmentIdsByKeyword(complaint);
        if (!kbIds.isEmpty()) {
            return all.stream().filter(d -> kbIds.contains(d.id())).toList();
        }
        return List.of();
    }

    private List<TriageDepartment> toTriageDepartments(List<Department> departments) {
        return departments.stream()
                .map(d -> new TriageDepartment(d.id(), d.name())).toList();
    }

    private String toJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Serialize department ids failed");
            return "[]";
        }
    }
}
