package com.sdas.server.appointment.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者历史就诊记录实体（复诊资格判定依据，只读）。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.5。</p>
 *
 * @param id          主键
 * @param patientId   患者主键
 * @param visitDate   就诊日期
 * @param departmentId 科室
 * @param doctorId    医生
 * @param summary     就诊摘要
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record PatientHistoryVisit(Long id, Long patientId, LocalDate visitDate, Long departmentId,
                                 Long doctorId, String summary, LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
}
