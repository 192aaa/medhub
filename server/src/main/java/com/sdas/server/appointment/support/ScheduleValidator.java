package com.sdas.server.appointment.support;

import com.sdas.server.appointment.model.DoctorOnlineStatus;
import com.sdas.server.appointment.repository.DoctorRepository;
import com.sdas.server.appointment.repository.DoctorScheduleRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 排班可约性校验（FR-004 / FR-003）：医生须在线、排班须存在且属于该医生、号源未约满。
 *
 * <p>任一不满足映射 {@link ErrorCode#INVALID_SCHEDULE}（前端据此提示「暂无医生在线」等）。</p>
 */
@Service
public class ScheduleValidator {

    private final DoctorRepository doctorRepository;

    private final DoctorScheduleRepository scheduleRepository;

    /**
     * 构造排班校验器。
     *
     * @param doctorRepository   医生仓储
     * @param scheduleRepository 排班仓储
     */
    public ScheduleValidator(DoctorRepository doctorRepository,
                             DoctorScheduleRepository scheduleRepository) {
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * 断言医生与排班可预约。
     *
     * @param doctorId  医生主键
     * @param scheduleId 排班主键
     */
    public void assertBookable(long doctorId, long scheduleId) {
        DoctorOnlineStatus onlineStatus = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCTOR_NOT_FOUND))
                .onlineStatus();
        if (onlineStatus == DoctorOnlineStatus.OFFLINE) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE);
        }
        var schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SCHEDULE));
        if (!schedule.doctorId().equals(doctorId)) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE);
        }
        if (schedule.slotsBooked() >= schedule.slotsTotal()) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE);
        }
    }
}
