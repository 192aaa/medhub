package com.sdas.server.doctor.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationImageRepository;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.doctor.dto.AcceptResponse;
import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.doctor.repository.ConsultationMessageRepository;
import com.sdas.server.doctor.service.impl.WorkbenchServiceImpl;
import com.sdas.server.identity.repository.PatientIdentityRepository;
import com.sdas.server.identity.service.AuditEventService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** WorkbenchService 单测（T044）：接诊 CAS、回复字数/状态校验、越权。 */
class WorkbenchServiceTest {

    private ConsultationRepository consultationRepository;
    private WorkbenchServiceImpl service;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationRepository.class);
        ConsultationImageRepository imageRepository = mock(ConsultationImageRepository.class);
        PatientIdentityRepository patientIdentityRepository = mock(PatientIdentityRepository.class);
        ConsultationMessageRepository messageRepository = mock(ConsultationMessageRepository.class);
        DoctorProperties properties = new DoctorProperties(24, 1000, 3,
                new DoctorProperties.Signature("p", "a", "pw", "SHA256withRSA"),
                new DoctorProperties.Prescription("url", "示例医院"));
        AuditEventService audit = mock(AuditEventService.class);
        service = new WorkbenchServiceImpl(consultationRepository, imageRepository,
                patientIdentityRepository, messageRepository, properties, audit);
    }

    @Test
    void acceptPendingConsultationSucceeds() {
        Consultation pending = consultation(701L, 101L, ConsultationStatus.PENDING);
        Consultation accepted = consultation(701L, 101L, ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findById(701L))
                .thenReturn(Optional.of(pending), Optional.of(accepted));
        when(consultationRepository.accept(701L, 101L)).thenReturn(1);

        AcceptResponse response = service.accept(101L, 701L);
        assertEquals("IN_PROGRESS", response.status());
    }

    @Test
    void acceptConflictThrows() {
        Consultation pending = consultation(701L, 101L, ConsultationStatus.PENDING);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(pending));
        when(consultationRepository.accept(701L, 101L)).thenReturn(0);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.accept(101L, 701L));
        assertEquals(ErrorCode.CONSULTATION_ACCEPT_CONFLICT, exception.getErrorCode());
    }

    @Test
    void replyTooLongThrows() {
        String longContent = "x".repeat(1001);
        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.reply(101L, 701L, longContent));
        assertEquals(ErrorCode.INVALID_REPLY_FORMAT, exception.getErrorCode());
    }

    @Test
    void replyWhenNotAcceptedThrows() {
        Consultation pending = consultation(701L, 101L, ConsultationStatus.PENDING);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(pending));
        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.reply(101L, 701L, "hello"));
        assertEquals(ErrorCode.INVALID_CONSULTATION_STATUS, exception.getErrorCode());
    }

    @Test
    void detailForbiddenWhenNotOwner() {
        Consultation other = consultation(701L, 102L, ConsultationStatus.PENDING);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(other));
        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.detail(101L, 701L));
        assertEquals(ErrorCode.CONSULTATION_FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void listReturnsItemsForDoctor() {
        Consultation item = consultation(701L, 101L, ConsultationStatus.PENDING);
        when(consultationRepository.findByDoctorIdAndStatus(101L, ConsultationStatus.PENDING))
                .thenReturn(List.of(item));
        assertEquals(1, service.list(101L, ConsultationStatus.PENDING).size());
    }

    private Consultation consultation(long id, long doctorId, ConsultationStatus status) {
        return new Consultation(id, 1L, 10001L, doctorId, 1L, "咳嗽三天", null, null, status,
                null, null, ConsultationTimeoutStatus.NONE, null, null, null, null, 0);
    }
}
