package com.sdas.server.appointment.service.impl;

import com.sdas.server.appointment.dto.AppointmentCreateRequest;
import com.sdas.server.appointment.dto.AppointmentCreateResponse;
import com.sdas.server.appointment.entity.Appointment;
import com.sdas.server.appointment.entity.Doctor;
import com.sdas.server.appointment.entity.DoctorSchedule;
import com.sdas.server.appointment.model.AppointmentStatus;
import com.sdas.server.appointment.model.PaymentStatus;
import com.sdas.server.appointment.repository.AppointmentRepository;
import com.sdas.server.appointment.repository.DoctorRepository;
import com.sdas.server.appointment.repository.DoctorScheduleRepository;
import com.sdas.server.appointment.service.AppointmentService;
import com.sdas.server.appointment.support.RevisitEligibilityService;
import com.sdas.server.appointment.support.ScheduleValidator;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 挂号预约实现（端点 5）。同一患者同排班幂等。 */
@Service
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;

    private final DoctorRepository doctorRepository;

    private final DoctorScheduleRepository scheduleRepository;

    private final ScheduleValidator scheduleValidator;

    private final RevisitEligibilityService revisitEligibilityService;

    private final AuditEventService auditEventService;

    /**
     * 构造挂号服务。
     *
     * @param appointmentRepository       预约仓储
     * @param doctorRepository           医生仓储
     * @param scheduleRepository         排班仓储
     * @param scheduleValidator          排班校验
     * @param revisitEligibilityService  复诊门禁
     * @param auditEventService          审计
     */
    public AppointmentServiceImpl(AppointmentRepository appointmentRepository,
                                DoctorRepository doctorRepository,
                                DoctorScheduleRepository scheduleRepository,
                                ScheduleValidator scheduleValidator,
                                RevisitEligibilityService revisitEligibilityService,
                                AuditEventService auditEventService) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleValidator = scheduleValidator;
        this.revisitEligibilityService = revisitEligibilityService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional
    public AppointmentCreateResponse create(long patientId, AppointmentCreateRequest request) {
        revisitEligibilityService.assertEligible(patientId);
        scheduleValidator.assertBookable(request.doctorId(), request.scheduleId());
        if (appointmentRepository.findByPatientAndSchedule(patientId, request.scheduleId()).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
        Doctor doctor = doctorRepository.findById(request.doctorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCTOR_NOT_FOUND));
        DoctorSchedule schedule = scheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SCHEDULE));

        Appointment appointment = new Appointment(null, patientId, doctor.id(), doctor.departmentId(),
                schedule.id(), schedule.workDate(), schedule.period(), doctor.consultationFee(),
                PaymentStatus.UNPAID, AppointmentStatus.PENDING_PAYMENT, request.patientNote(),
                null, null, 0);
        long id = appointmentRepository.insert(appointment);
        auditEventService.record(AuditEventType.APPOINTMENT_CREATED, "SUCCESS", patientId, null, null,
                null);
        return new AppointmentCreateResponse(id, doctor.id(), doctor.departmentId(),
                schedule.workDate(), schedule.period().name(), doctor.consultationFee(),
                PaymentStatus.UNPAID.name(), AppointmentStatus.PENDING_PAYMENT.name(),
                LocalDateTime.now());
    }
}
