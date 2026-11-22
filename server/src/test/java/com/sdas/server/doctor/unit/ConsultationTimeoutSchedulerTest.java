package com.sdas.server.doctor.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.doctor.support.ConsultationTimeoutScheduler;
import com.sdas.server.identity.service.AuditEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ConsultationTimeoutScheduler 单测（T078）：超时触发、幂等去重、无候选不触发。 */
class ConsultationTimeoutSchedulerTest {

    @Test
    void marksTimeoutCandidates() {
        ConsultationRepository repository = mock(ConsultationRepository.class);
        AuditEventService audit = mock(AuditEventService.class);
        DoctorProperties properties = new DoctorProperties(24, 1000, 3,
                new DoctorProperties.Signature("p", "a", "pw", "SHA256withRSA"),
                new DoctorProperties.Prescription("url", "示例医院"));
        Consultation candidate = new Consultation(701L, 1L, 10001L, 101L, 1L, "咳嗽", null, null,
                ConsultationStatus.IN_PROGRESS, LocalDateTime.now().minusHours(25), 101L,
                ConsultationTimeoutStatus.NONE, null, null, null, null, 0);
        when(repository.findTimeoutCandidates(any(LocalDateTime.class)))
                .thenReturn(List.of(candidate));
        when(repository.markTimeout(701L)).thenReturn(1);

        new ConsultationTimeoutScheduler(repository, properties, audit).scanAndMarkTimeout();
        verify(repository).markTimeout(701L);
        verify(audit).record(com.sdas.server.identity.model.AuditEventType.CONSULTATION_TIMEOUT,
                "SUCCESS", 10001L, null, null, null);
    }

    @Test
    void skipsWhenMarkTimeoutReturnsZero() {
        ConsultationRepository repository = mock(ConsultationRepository.class);
        AuditEventService audit = mock(AuditEventService.class);
        DoctorProperties properties = new DoctorProperties(24, 1000, 3,
                new DoctorProperties.Signature("p", "a", "pw", "SHA256withRSA"),
                new DoctorProperties.Prescription("url", "示例医院"));
        Consultation candidate = new Consultation(701L, 1L, 10001L, 101L, 1L, "咳嗽", null, null,
                ConsultationStatus.IN_PROGRESS, LocalDateTime.now().minusHours(25), 101L,
                ConsultationTimeoutStatus.NONE, null, null, null, null, 0);
        when(repository.findTimeoutCandidates(any(LocalDateTime.class)))
                .thenReturn(List.of(candidate));
        when(repository.markTimeout(701L)).thenReturn(0);

        new ConsultationTimeoutScheduler(repository, properties, audit).scanAndMarkTimeout();
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }
}
