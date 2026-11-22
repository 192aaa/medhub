package com.sdas.server.appointment.service.impl;

import com.sdas.server.appointment.dto.PaymentRequest;
import com.sdas.server.appointment.dto.PaymentResponse;
import com.sdas.server.appointment.dto.PaymentStatusResponse;
import com.sdas.server.appointment.entity.Appointment;
import com.sdas.server.appointment.entity.PaymentRecord;
import com.sdas.server.appointment.model.AppointmentStatus;
import com.sdas.server.appointment.model.PaymentChannel;
import com.sdas.server.appointment.model.PaymentRecordStatus;
import com.sdas.server.appointment.model.PaymentStatus;
import com.sdas.server.appointment.repository.AppointmentRepository;
import com.sdas.server.appointment.repository.PaymentRecordRepository;
import com.sdas.server.appointment.service.PaymentService;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.AppointmentProperties;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 挂号费支付实现（端点 6a/6b，MVP 模拟支付，幂等）。 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String MOCK_CHANNEL = "MOCK";

    private final AppointmentRepository appointmentRepository;

    private final PaymentRecordRepository paymentRecordRepository;

    private final AuditEventService auditEventService;

    private final AppointmentProperties appointmentProperties;

    /**
     * 构造支付服务。
     *
     * @param appointmentRepository   预约仓储
     * @param paymentRecordRepository 支付流水仓储
     * @param auditEventService      审计
     * @param appointmentProperties  配置（控制模拟支付开关）
     */
    public PaymentServiceImpl(AppointmentRepository appointmentRepository,
                             PaymentRecordRepository paymentRecordRepository,
                             AuditEventService auditEventService,
                             AppointmentProperties appointmentProperties) {
        this.appointmentRepository = appointmentRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.auditEventService = auditEventService;
        this.appointmentProperties = appointmentProperties;
    }

    @Override
    @Transactional
    public PaymentResponse pay(long patientId, long appointmentId, PaymentRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND));
        if (!appointment.patientId().equals(patientId)) {
            throw new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        if (appointment.appointmentStatus() != AppointmentStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        if (request.amount() == null || request.amount().compareTo(appointment.feeAmount()) != 0) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }
        if (!MOCK_CHANNEL.equals(request.channel())) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }
        if (paymentRecordRepository.existsByAppointmentId(appointmentId)) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFLICT);
        }
        if (!appointmentProperties.mockPaymentEnabled()) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        String outTradeNo = MOCK_CHANNEL + System.nanoTime();
        PaymentRecord record = new PaymentRecord(null, appointmentId, PaymentChannel.MOCK,
                appointment.feeAmount(), outTradeNo, PaymentRecordStatus.SUCCESS, LocalDateTime.now(),
                null, null, 0);
        long paymentId = paymentRecordRepository.insert(record);

        int rows = appointmentRepository.updatePaymentStatus(appointmentId, PaymentStatus.PAID,
                AppointmentStatus.PAID, appointment.version());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        auditEventService.record(AuditEventType.APPOINTMENT_PAID, "SUCCESS", patientId, null, null,
                null);
        return new PaymentResponse(appointmentId, paymentId, appointment.feeAmount(),
                PaymentChannel.MOCK.name(), PaymentRecordStatus.SUCCESS.name(), LocalDateTime.now(),
                PaymentStatus.PAID.name(), AppointmentStatus.PAID.name());
    }

    @Override
    public PaymentStatusResponse query(long patientId, long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND));
        if (!appointment.patientId().equals(patientId)) {
            throw new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        Optional<PaymentRecord> record = paymentRecordRepository.findByAppointmentId(appointmentId);
        if (record.isEmpty()) {
            return new PaymentStatusResponse(appointmentId, null, null, null,
                    PaymentStatus.UNPAID.name(), null, appointment.paymentStatus().name(),
                    appointment.appointmentStatus().name());
        }
        PaymentRecord paid = record.get();
        return new PaymentStatusResponse(appointmentId, paid.id(), paid.amount(),
                paid.channel().name(), paid.status().name(), paid.paidAt(),
                appointment.paymentStatus().name(), appointment.appointmentStatus().name());
    }
}
