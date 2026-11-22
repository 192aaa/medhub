package com.sdas.server.appointment.service.impl;

import com.sdas.server.appointment.dto.ConsultationDetailResponse;
import com.sdas.server.appointment.dto.ConsultationImageDto;
import com.sdas.server.appointment.dto.ConsultationSubmitRequest;
import com.sdas.server.appointment.dto.ConsultationSubmitResponse;
import com.sdas.server.appointment.entity.Appointment;
import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.entity.Department;
import com.sdas.server.appointment.entity.Doctor;
import com.sdas.server.appointment.model.AppointmentStatus;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.AppointmentRepository;
import com.sdas.server.appointment.repository.ConsultationImageRepository;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.appointment.repository.DepartmentRepository;
import com.sdas.server.appointment.repository.DoctorRepository;
import com.sdas.server.appointment.service.ConsultationService;
import com.sdas.server.appointment.service.SummaryClient;
import com.sdas.server.common.BusinessException;
import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.ImageStorageProperties;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 图文问诊单实现（端点 8/9）。 */
@Service
public class ConsultationServiceImpl implements ConsultationService {

    private final AppointmentRepository appointmentRepository;

    private final ConsultationRepository consultationRepository;

    private final ConsultationImageRepository imageRepository;

    private final DoctorRepository doctorRepository;

    private final DepartmentRepository departmentRepository;

    private final SummaryClient summaryClient;

    private final AuditEventService auditEventService;

    private final ImageStorageProperties imageStorageProperties;

    /**
     * 构造问诊单服务。
     *
     * @param appointmentRepository   预约仓储
     * @param consultationRepository  问诊单仓储
     * @param imageRepository        图片仓储
     * @param doctorRepository       医生仓储
     * @param departmentRepository   科室仓储
     * @param summaryClient          AI 摘要客户端
     * @param auditEventService      审计
     * @param imageStorageProperties 图片配置（限制图片数量）
     */
    public ConsultationServiceImpl(AppointmentRepository appointmentRepository,
                                 ConsultationRepository consultationRepository,
                                 ConsultationImageRepository imageRepository,
                                 DoctorRepository doctorRepository,
                                 DepartmentRepository departmentRepository,
                                 SummaryClient summaryClient,
                                 AuditEventService auditEventService,
                                 ImageStorageProperties imageStorageProperties) {
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.imageRepository = imageRepository;
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.summaryClient = summaryClient;
        this.auditEventService = auditEventService;
        this.imageStorageProperties = imageStorageProperties;
    }

    @Override
    @Transactional
    public ConsultationSubmitResponse submit(long patientId, ConsultationSubmitRequest request) {
        String complaint = request.complaint();
        validateComplaint(complaint);
        validateDescription(request.description());
        validateImageCount(request.imageUrls());

        Appointment appointment = appointmentRepository.findById(request.appointmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND));
        if (!appointment.patientId().equals(patientId)) {
            throw new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        if (appointment.appointmentStatus() != AppointmentStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        if (consultationRepository.existsByAppointmentId(request.appointmentId())) {
            throw new BusinessException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }

        Doctor doctor = doctorRepository.findById(appointment.doctorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCTOR_NOT_FOUND));
        Department department = departmentRepository.findById(appointment.departmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));

        String summary = summaryClient.summarize(complaint, request.description());
        Consultation consultation = new Consultation(null, request.appointmentId(), patientId,
                doctor.id(), department.id(), complaint, request.description(), summary,
                ConsultationStatus.PENDING, null, null, ConsultationTimeoutStatus.NONE, null, null,
                null, null, 0);
        long consultationId = consultationRepository.insert(consultation);

        int linked = 0;
        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            linked = imageRepository.linkByUrls(consultationId, request.imageUrls());
        }
        auditEventService.record(AuditEventType.CONSULTATION_SUBMITTED, "SUCCESS", patientId, null,
                null, null);
        return new ConsultationSubmitResponse(consultationId, request.appointmentId(), doctor.id(),
                department.id(), complaint, linked, ConsultationStatus.PENDING.name(), summary,
                LocalDateTime.now());
    }

    @Override
    public ConsultationDetailResponse detail(long patientId, long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        if (!consultation.patientId().equals(patientId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_FORBIDDEN);
        }
        List<ConsultationImageDto> images = imageRepository.findByConsultationId(consultationId).stream()
                .map(image -> new ConsultationImageDto(image.id(), image.imageUrl())).toList();
        return new ConsultationDetailResponse(consultation.id(), consultation.appointmentId(),
                consultation.doctorId(), consultation.departmentId(), consultation.complaint(),
                consultation.description(), images, consultation.summary(),
                consultation.status().name(), consultation.createdAt(), consultation.updatedAt());
    }

    /** 主诉长度须在 10–200 字符之间。 */
    private void validateComplaint(String complaint) {
        if (complaint == null || complaint.length() < 10 || complaint.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_COMPLAINT_FORMAT);
        }
    }

    /** 病情描述可空，最长 2000 字符。 */
    private void validateDescription(String description) {
        if (description != null && description.length() > 2000) {
            throw new BusinessException(ErrorCode.INVALID_DESCRIPTION_FORMAT);
        }
    }

    /** 图片数量不得超过配置上限。 */
    private void validateImageCount(List<String> imageUrls) {
        if (imageUrls != null && imageUrls.size() > imageStorageProperties.maxCount()) {
            throw new BusinessException(ErrorCode.TOO_MANY_IMAGES);
        }
    }
}
