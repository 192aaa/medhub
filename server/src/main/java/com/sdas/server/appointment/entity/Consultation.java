package com.sdas.server.appointment.entity;

import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import java.time.LocalDateTime;

/**
 * 图文问诊单实体。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.8；
 * 接诊扩展列来源：specs/003-doctor-consultation/data-model.md 2.2。
 * UNIQUE(appointment_id) 保证幂等；summary 为 AI 摘要，仅供医生侧参考。</p>
 *
 * @param id                    主键
 * @param appointmentId         关联挂号单
 * @param patientId            患者
 * @param doctorId             医生
 * @param departmentId         科室
 * @param complaint            主诉
 * @param description          选填描述
 * @param summary              AI 病情摘要
 * @param status               问诊单状态
 * @param acceptedAt           接诊时间（null=未接诊）
 * @param acceptedByDoctorId   接诊医生
 * @param timeoutStatus        超时状态
 * @param timeoutHandledAt     超时兜底执行时间
 * @param transferredToDoctorId 转诊目标医生（预留）
 * @param createdAt            创建时间
 * @param updatedAt            更新时间
 * @param version              乐观锁
 */
public record Consultation(Long id, Long appointmentId, Long patientId, Long doctorId,
                           Long departmentId, String complaint, String description, String summary,
                           ConsultationStatus status, LocalDateTime acceptedAt, Long acceptedByDoctorId,
                           ConsultationTimeoutStatus timeoutStatus, LocalDateTime timeoutHandledAt,
                           Long transferredToDoctorId, LocalDateTime createdAt, LocalDateTime updatedAt,
                           int version) {
}
