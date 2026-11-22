package com.sdas.server.appointment.service.impl;

import com.sdas.server.appointment.dto.DepartmentResponse;
import com.sdas.server.appointment.dto.DoctorDetailResponse;
import com.sdas.server.appointment.dto.DoctorResponse;
import com.sdas.server.appointment.dto.ScheduleResponse;
import com.sdas.server.appointment.entity.Department;
import com.sdas.server.appointment.entity.Doctor;
import com.sdas.server.appointment.entity.DoctorSchedule;
import com.sdas.server.appointment.model.DoctorOnlineStatus;
import com.sdas.server.appointment.repository.DepartmentRepository;
import com.sdas.server.appointment.repository.DoctorRepository;
import com.sdas.server.appointment.repository.DoctorScheduleRepository;
import com.sdas.server.appointment.service.DepartmentReadService;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;

/** 科室与医生只读查询实现（端点 1/2/3）。 */
@Service
public class DepartmentReadServiceImpl implements DepartmentReadService {

    private final DepartmentRepository departmentRepository;

    private final DoctorRepository doctorRepository;

    private final DoctorScheduleRepository scheduleRepository;

    /**
     * 构造只读服务。
     *
     * @param departmentRepository 科室仓储
     * @param doctorRepository     医生仓储
     * @param scheduleRepository   排班仓储
     */
    public DepartmentReadServiceImpl(DepartmentRepository departmentRepository,
                                    DoctorRepository doctorRepository,
                                    DoctorScheduleRepository scheduleRepository) {
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    public List<DepartmentResponse> listDepartments() {
        return departmentRepository.findAllEnabled().stream().map(this::toDepartment).toList();
    }

    @Override
    public List<DoctorResponse> listDoctors(Long departmentId, String online) {
        DoctorOnlineStatus status = parseOnline(online);
        List<Doctor> doctors;
        if (departmentId != null && status != null) {
            doctors = doctorRepository.findByDepartmentIdAndOnlineStatus(departmentId, status.name());
        } else if (departmentId != null) {
            doctors = doctorRepository.findAll().stream()
                    .filter(d -> departmentId.equals(d.departmentId())).toList();
        } else if (status != null) {
            doctors = doctorRepository.findAll().stream()
                    .filter(d -> d.onlineStatus() == status).toList();
        } else {
            doctors = doctorRepository.findAll();
        }
        return doctors.stream().map(this::toDoctor).toList();
    }

    @Override
    public DoctorDetailResponse detail(long doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCTOR_NOT_FOUND));
        List<ScheduleResponse> schedules = scheduleRepository.findByDoctorId(doctorId).stream()
                .map(this::toSchedule).toList();
        return new DoctorDetailResponse(doctor.id(), doctor.name(), doctor.title(),
                doctor.departmentId(), doctor.hospital(), doctor.yearsOfExperience(),
                doctor.goodAt(), doctor.consultationFee(), doctor.rating(),
                doctor.consultationCount(), doctor.onlineStatus().name(), doctor.avatarUrl(),
                schedules);
    }

    private DepartmentResponse toDepartment(Department d) {
        return new DepartmentResponse(d.id(), d.name(), d.description(), d.iconUrl(), d.sortOrder(),
                d.enabled());
    }

    private DoctorResponse toDoctor(Doctor d) {
        return new DoctorResponse(d.id(), d.name(), d.title(), d.departmentId(), d.hospital(),
                d.yearsOfExperience(), d.goodAt(), d.consultationFee(), d.rating(),
                d.consultationCount(), d.onlineStatus().name(), d.avatarUrl());
    }

    private ScheduleResponse toSchedule(DoctorSchedule s) {
        return new ScheduleResponse(s.id(), s.workDate(), s.period().name(), s.slotsTotal(),
                s.slotsBooked());
    }

    private DoctorOnlineStatus parseOnline(String online) {
        if (online == null || online.isBlank()) {
            return null;
        }
        try {
            return DoctorOnlineStatus.valueOf(online);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
