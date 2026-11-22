package com.sdas.server.doctor.service.impl;

import com.sdas.server.appointment.dto.ConsultationImageDto;
import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationImageRepository;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.doctor.dto.AcceptResponse;
import com.sdas.server.doctor.dto.ConsultationDetailResponse;
import com.sdas.server.doctor.dto.ConsultationListItem;
import com.sdas.server.doctor.dto.MessageItem;
import com.sdas.server.doctor.dto.MessageResponse;
import com.sdas.server.doctor.entity.ConsultationMessage;
import com.sdas.server.doctor.model.MessageType;
import com.sdas.server.doctor.repository.ConsultationMessageRepository;
import com.sdas.server.doctor.service.WorkbenchService;
import com.sdas.server.identity.entity.PatientIdentity;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.repository.PatientIdentityRepository;
import com.sdas.server.identity.service.AuditEventService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 医生工作台实现：待接诊/已接诊列表（患者姓名脱敏）、问诊单详情（含图片与摘要），
 * 强制 doctor_id 归属校验（越权 CONSULTATION_FORBIDDEN）。
 */
@Service
public class WorkbenchServiceImpl implements WorkbenchService {

    private final ConsultationRepository consultationRepository;

    private final ConsultationImageRepository imageRepository;

    private final PatientIdentityRepository patientIdentityRepository;

    private final ConsultationMessageRepository messageRepository;

    private final DoctorProperties properties;

    private final AuditEventService auditEventService;

    /**
     * 构造工作台服务。
     *
     * @param consultationRepository      问诊单仓储
     * @param imageRepository             图片仓储
     * @param patientIdentityRepository   患者实名信息仓储
     * @param messageRepository           对话消息仓储
     * @param properties                  医生接诊配置
     * @param auditEventService           审计
     */
    public WorkbenchServiceImpl(ConsultationRepository consultationRepository,
                                ConsultationImageRepository imageRepository,
                                PatientIdentityRepository patientIdentityRepository,
                                ConsultationMessageRepository messageRepository,
                                DoctorProperties properties,
                                AuditEventService auditEventService) {
        this.consultationRepository = consultationRepository;
        this.imageRepository = imageRepository;
        this.patientIdentityRepository = patientIdentityRepository;
        this.messageRepository = messageRepository;
        this.properties = properties;
        this.auditEventService = auditEventService;
    }

    @Override
    public List<ConsultationListItem> list(long doctorId, ConsultationStatus status) {
        return consultationRepository.findByDoctorIdAndStatus(doctorId, status).stream()
                .map(consultation -> new ConsultationListItem(consultation.id(),
                        patientName(consultation.patientId()), consultation.complaint(),
                        consultation.status().name(), consultation.createdAt()))
                .toList();
    }

    @Override
    public ConsultationDetailResponse detail(long doctorId, long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        if (!consultation.doctorId().equals(doctorId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_FORBIDDEN);
        }
        List<ConsultationImageDto> images = imageRepository.findByConsultationId(consultationId)
                .stream()
                .map(image -> new ConsultationImageDto(image.id(), image.imageUrl()))
                .toList();
        return new ConsultationDetailResponse(consultation.id(), patientName(consultation.patientId()),
                consultation.complaint(), consultation.description(), images,
                consultation.summary(), consultation.status().name(), consultation.acceptedAt(),
                consultation.createdAt());
    }

    @Override
    @Transactional
    public AcceptResponse accept(long doctorId, long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        if (!consultation.doctorId().equals(doctorId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_FORBIDDEN);
        }
        if (consultation.status() != ConsultationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_CONSULTATION_STATUS);
        }
        int updated = consultationRepository.accept(consultationId, doctorId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONSULTATION_ACCEPT_CONFLICT);
        }
        Consultation accepted = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        auditEventService.record(AuditEventType.CONSULTATION_ACCEPTED, "SUCCESS",
                consultation.patientId(), null, null, null);
        return new AcceptResponse(consultationId, accepted.status().name(),
                accepted.acceptedAt(), doctorId);
    }

    @Override
    public List<MessageItem> listMessages(long doctorId, long consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        if (!consultation.doctorId().equals(doctorId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_FORBIDDEN);
        }
        return messageRepository.findByConsultationId(consultationId).stream()
                .map(message -> new MessageItem(message.id(), message.senderType().name(),
                        message.content(), message.createdAt()))
                .toList();
    }

    @Override
    @Transactional
    public MessageResponse reply(long doctorId, long consultationId, String content) {
        validateReply(content);
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        if (!consultation.doctorId().equals(doctorId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_FORBIDDEN);
        }
        if (consultation.status() != ConsultationStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_CONSULTATION_STATUS);
        }
        long messageId = messageRepository.insert(new ConsultationMessage(null, consultationId,
                MessageType.DOCTOR, doctorId, content, null));
        auditEventService.record(AuditEventType.CONSULTATION_REPLIED, "SUCCESS",
                consultation.patientId(), null, null, null);
        java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
        return new MessageResponse(messageId, MessageType.DOCTOR.name(), content, createdAt);
    }

    private void validateReply(String content) {
        if (content == null || content.isBlank()
                || content.length() > properties.replyMaxChars()) {
            throw new BusinessException(ErrorCode.INVALID_REPLY_FORMAT);
        }
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new BusinessException(ErrorCode.INVALID_REPLY_FORMAT);
            }
        }
    }

    private String patientName(long patientId) {
        return patientIdentityRepository.findByPatientId(patientId)
                .map(PatientIdentity::realNameMask)
                .filter(name -> name != null && !name.isBlank())
                .orElse("匿*");
    }
}
