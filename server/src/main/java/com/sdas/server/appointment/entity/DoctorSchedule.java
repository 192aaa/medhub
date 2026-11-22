package com.sdas.server.appointment.entity;

import com.sdas.server.appointment.model.SchedulePeriod;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医生排班实体。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.3。</p>
 *
 * @param id          主键
 * @param doctorId    医生主键
 * @param workDate    出诊日期
 * @param period      时段
 * @param slotsTotal  总号源
 * @param slotsBooked 已约号源
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record DoctorSchedule(Long id, Long doctorId, LocalDate workDate, SchedulePeriod period,
                             int slotsTotal, int slotsBooked, LocalDateTime createdAt,
                             LocalDateTime updatedAt) {
}
