package com.sdas.server.healthconsultation.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sdas.server.appointment.entity.Department;
import com.sdas.server.appointment.repository.DepartmentRepository;
import com.sdas.server.config.HealthEmergencyProperties;
import com.sdas.server.healthconsultation.entity.EmergencyDecision;
import com.sdas.server.healthconsultation.model.EmergencyVerdict;
import com.sdas.server.healthconsultation.repository.EmergencyDecisionRepository;
import com.sdas.server.healthconsultation.service.EmergencySymptomService.Evaluation;
import com.sdas.server.healthconsultation.service.impl.EmergencySymptomServiceImpl;
import com.sdas.server.healthconsultation.support.EmergencyDecisionResolver;
import com.sdas.server.healthconsultation.support.EmergencyHeaderParser.ModelEmergencySignal;
import com.sdas.server.healthconsultation.support.EmergencyRuleMatcher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 规则 + 模型复核 + 留痕 + booking 反查。 */
class EmergencySymptomServiceTest {

    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

    private final EmergencyDecisionRepository decisionRepository =
            mock(EmergencyDecisionRepository.class);

    private final EmergencySymptomServiceImpl service = new EmergencySymptomServiceImpl(
            new EmergencyRuleMatcher(), new EmergencyDecisionResolver(),
            new HealthEmergencyProperties(List.of()), departmentRepository, decisionRepository);

    @Test
    @DisplayName("规则命中 + 模型 YES → 预警并回填科室")
    void confirmedWithBooking() {
        when(decisionRepository.insert(any(EmergencyDecision.class))).thenReturn(88L);
        when(departmentRepository.findEnabledByName("心血管内科")).thenReturn(Optional.of(
                new Department(1L, "心血管内科", null, null, 1, true,
                        LocalDateTime.now(), LocalDateTime.now())));

        Evaluation evaluation = service.evaluate(1L, "胸口疼得厉害，喘不上气",
                ModelEmergencySignal.YES);

        assertThat(evaluation.shouldWarn()).isTrue();
        assertThat(evaluation.result().verdict()).isEqualTo(EmergencyVerdict.CONFIRMED);
        assertThat(evaluation.payload().booking()).isNotNull();
        assertThat(evaluation.payload().booking().departmentName()).isEqualTo("心血管内科");
        verify(decisionRepository).insert(any(EmergencyDecision.class));
    }

    @Test
    @DisplayName("规则命中 + 模型 NO → 留痕但不预警")
    void rejectedLeavesTrace() {
        when(decisionRepository.insert(any(EmergencyDecision.class))).thenReturn(89L);

        Evaluation evaluation = service.evaluate(1L, "胸闷得厉害",
                ModelEmergencySignal.NO);

        assertThat(evaluation.shouldWarn()).isFalse();
        assertThat(evaluation.result().verdict()).isEqualTo(EmergencyVerdict.REJECTED);
        assertThat(evaluation.decisionId()).isEqualTo(89L);
        verify(decisionRepository).insert(any(EmergencyDecision.class));
    }

    @Test
    @DisplayName("模型不可用但规则命中 → 仍预警（UNAVAILABLE）")
    void unavailableStillWarns() {
        when(decisionRepository.insert(any(EmergencyDecision.class))).thenReturn(90L);

        Evaluation evaluation = service.evaluate(1L, "突然说话不清，一侧无力",
                ModelEmergencySignal.UNAVAILABLE);

        assertThat(evaluation.shouldWarn()).isTrue();
        assertThat(evaluation.result().verdict()).isEqualTo(EmergencyVerdict.UNAVAILABLE);
    }

    @Test
    @DisplayName("规则与模型均未命中 → 不产生留痕")
    void noRecordWhenNeither() {
        Evaluation evaluation = service.evaluate(1L, "有点困",
                ModelEmergencySignal.NO);

        assertThat(evaluation.result()).isNull();
        assertThat(evaluation.shouldWarn()).isFalse();
        verify(decisionRepository, org.mockito.Mockito.never())
                .insert(any(EmergencyDecision.class));
    }

    @Test
    @DisplayName("助手消息落库后回填 message_id")
    void attachMessage() {
        service.attachMessage(88L, 90002L);
        verify(decisionRepository).fillMessageId(88L, 90002L);
    }

    @Test
    @DisplayName("booking 反查失败时仍预警但 booking 为空")
    void bookingMissing() {
        when(decisionRepository.insert(any(EmergencyDecision.class))).thenReturn(91L);
        when(departmentRepository.findEnabledByName(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        Evaluation evaluation = service.evaluate(1L, "大出血止不住",
                ModelEmergencySignal.YES);

        assertThat(evaluation.shouldWarn()).isTrue();
        assertThat(evaluation.payload().booking()).isNull();
    }

    @Test
    @DisplayName("仅模型判定（MODEL_ONLY）时规则码为 MODEL_ONLY 且反查急诊科")
    void modelOnly() {
        when(decisionRepository.insert(any(EmergencyDecision.class))).thenReturn(92L);
        when(departmentRepository.findEnabledByName("急诊科")).thenReturn(Optional.of(
                new Department(2L, "急诊科", null, null, 2, true,
                        LocalDateTime.now(), LocalDateTime.now())));

        Evaluation evaluation = service.evaluate(1L, "我感觉很不对劲",
                ModelEmergencySignal.YES);

        assertThat(evaluation.payload().ruleCode())
                .isEqualTo(EmergencyDecisionResolver.MODEL_ONLY_RULE_CODE);
        assertThat(evaluation.payload().booking()).isNotNull();
    }
}
