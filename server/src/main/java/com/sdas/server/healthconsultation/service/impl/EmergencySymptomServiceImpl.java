package com.sdas.server.healthconsultation.service.impl;

import com.sdas.server.appointment.repository.DepartmentRepository;
import com.sdas.server.config.EmergencyRule;
import com.sdas.server.config.HealthEmergencyProperties;
import com.sdas.server.healthconsultation.dto.HealthSseEvents;
import com.sdas.server.healthconsultation.entity.EmergencyDecision;
import com.sdas.server.healthconsultation.repository.EmergencyDecisionRepository;
import com.sdas.server.healthconsultation.service.EmergencySymptomService;
import com.sdas.server.healthconsultation.support.EmergencyDecisionResolver;
import com.sdas.server.healthconsultation.support.EmergencyHeaderParser.ModelEmergencySignal;
import com.sdas.server.healthconsultation.support.EmergencyRuleMatcher;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 紧急症状判定实现（M5）。
 *
 * <p>流程：规则匹配 → 与模型首行结论合并（{@link EmergencyDecisionResolver} 矩阵）→
 * 写判定留痕（规则码/结论/是否预警，禁止病情明文）→ 需要预警时按规则
 * {@code departmentName} 反查科室生成挂号预填。</p>
 */
@Service
public class EmergencySymptomServiceImpl implements EmergencySymptomService {

    private static final String DEFAULT_LEVEL = "HIGH";

    private static final String DEFAULT_ADVICE = "请立即拨打 120 或前往最近急诊";

    private static final String DEFAULT_TITLE = "症状可能存在紧急风险";

    private static final String FALLBACK_DEPARTMENT = "急诊科";

    private final EmergencyRuleMatcher ruleMatcher;

    private final EmergencyDecisionResolver decisionResolver;

    private final HealthEmergencyProperties properties;

    private final DepartmentRepository departmentRepository;

    private final EmergencyDecisionRepository decisionRepository;

    /**
     * 构造紧急判定实现。
     *
     * @param ruleMatcher         规则匹配器
     * @param decisionResolver    判定矩阵
     * @param properties          规则配置
     * @param departmentRepository 科室只读仓储
     * @param decisionRepository  留痕仓储
     */
    public EmergencySymptomServiceImpl(EmergencyRuleMatcher ruleMatcher,
                                       EmergencyDecisionResolver decisionResolver,
                                       HealthEmergencyProperties properties,
                                       DepartmentRepository departmentRepository,
                                       EmergencyDecisionRepository decisionRepository) {
        this.ruleMatcher = ruleMatcher;
        this.decisionResolver = decisionResolver;
        this.properties = properties;
        this.departmentRepository = departmentRepository;
        this.decisionRepository = decisionRepository;
    }

    @Override
    public Evaluation evaluate(long sessionId, String patientText, ModelEmergencySignal signal) {
        Optional<EmergencyRule> matched =
                ruleMatcher.match(patientText, properties.effectiveRules());
        Optional<EmergencyDecisionResolver.Result> resolved =
                decisionResolver.resolve(matched.map(EmergencyRule::code).orElse(null), signal);
        if (resolved.isEmpty()) {
            return new Evaluation(null, null, null);
        }
        EmergencyDecisionResolver.Result result = resolved.get();
        long decisionId = decisionRepository.insert(new EmergencyDecision(0L, sessionId, null,
                result.ruleCode(), result.verdict(), result.warned(), LocalDateTime.now()));
        if (!result.warned()) {
            return new Evaluation(decisionId, result, null);
        }
        return new Evaluation(decisionId, result, buildPayload(matched, result));
    }

    @Override
    public void attachMessage(Long decisionId, long messageId) {
        if (decisionId != null) {
            decisionRepository.fillMessageId(decisionId, messageId);
        }
    }

    private HealthSseEvents.Emergency buildPayload(Optional<EmergencyRule> rule,
                                                   EmergencyDecisionResolver.Result result) {
        String level = rule.map(EmergencyRule::level).orElse(DEFAULT_LEVEL);
        String title = rule.map(value -> value.name() + "：可能为紧急情况").orElse(DEFAULT_TITLE);
        String departmentName = rule.map(EmergencyRule::departmentName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(FALLBACK_DEPARTMENT);
        HealthSseEvents.Booking booking = departmentRepository.findEnabledByName(departmentName)
                .map(department -> new HealthSseEvents.Booking(department.id(), department.name()))
                .orElse(null);
        return new HealthSseEvents.Emergency(level, title, DEFAULT_ADVICE, result.ruleCode(),
                booking);
    }
}
