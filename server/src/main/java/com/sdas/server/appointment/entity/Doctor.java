package com.sdas.server.appointment.entity;

import com.sdas.server.appointment.model.DoctorOnlineStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医生实体（姓名为脱敏展示值，禁止存真实姓名）。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.2。</p>
 *
 * @param id                 主键
 * @param departmentId       所属科室
 * @param name              脱敏姓名（如 张*）
 * @param title             职称
 * @param hospital          医院
 * @param yearsOfExperience 从业年限
 * @param goodAt            擅长
 * @param consultationFee    挂号费（服务端定价）
 * @param rating            评分
 * @param consultationCount 接诊量
 * @param onlineStatus      在线状态
 * @param avatarUrl         头像
 * @param createdAt         创建时间
 * @param updatedAt         更新时间
 */
public record Doctor(Long id, Long departmentId, String name, String title, String hospital,
                     Integer yearsOfExperience, String goodAt, BigDecimal consultationFee,
                     BigDecimal rating, int consultationCount, DoctorOnlineStatus onlineStatus,
                     String avatarUrl, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
