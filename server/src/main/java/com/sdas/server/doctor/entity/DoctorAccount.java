package com.sdas.server.doctor.entity;

import com.sdas.server.doctor.model.DoctorAccountStatus;
import java.time.LocalDateTime;

/**
 * 医生账号实体（用户名 + BCrypt 密码 + 关联医生档案）。
 *
 * <p>表结构来源：specs/003-doctor-consultation/data-model.md 2.1。</p>
 *
 * @param id           主键
 * @param username     登录名
 * @param passwordHash BCrypt 哈希（强度 ≥ 10）
 * @param doctorId     关联医生档案
 * @param nameMask     脱敏姓名
 * @param status       账号状态
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @param version      乐观锁
 */
public record DoctorAccount(
        Long id,
        String username,
        String passwordHash,
        Long doctorId,
        String nameMask,
        DoctorAccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
