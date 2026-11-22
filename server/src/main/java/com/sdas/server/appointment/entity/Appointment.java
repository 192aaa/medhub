package com.sdas.server.appointment.entity;

import com.sdas.server.appointment.model.AppointmentStatus;
import com.sdas.server.appointment.model.PaymentStatus;
import com.sdas.server.appointment.model.SchedulePeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 挂号单实体。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.6。
 * paymentStatus 与 appointmentStatus 双字段由同一事务维护一致。</p>
 *
 * @param id                主键
 * @param patientId        患者
 * @param doctorId         医生
 * @param departmentId     科室
 * @param scheduleId       排班
 * @param appointmentDate  就诊日期
 * @param period           时段
 * @param feeAmount        挂号费（快照自 doctor.consultation_fee）
 * @param paymentStatus    支付状态
 * @param appointmentStatus 挂号单状态
 * @param patientNote      患者备注
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 * @param version          乐观锁
 */
public record Appointment(Long id, Long patientId, Long doctorId, Long departmentId, Long scheduleId,
                          LocalDate appointmentDate, SchedulePeriod period, BigDecimal feeAmount,
                          PaymentStatus paymentStatus, AppointmentStatus appointmentStatus,
                          String patientNote, LocalDateTime createdAt, LocalDateTime updatedAt,
                          int version) {
}
