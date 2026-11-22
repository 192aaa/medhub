package com.sdas.server.doctor.dto;

/**
 * 端点 1 响应 data。
 *
 * @param accessToken  医生 JWT（含「医生」角色）
 * @param refreshToken 刷新令牌（预留）
 * @param doctorId     医生主键
 * @param nameMask     脱敏姓名
 * @param title        职称
 * @param departmentId 所属科室
 * @param expiresIn    过期秒数
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long doctorId,
        String nameMask,
        String title,
        Long departmentId,
        long expiresIn) {
}
