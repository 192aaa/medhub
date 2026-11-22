package com.sdas.server.identity.service;

/**
 * 从医生 access token 解析出的身份声明。
 *
 * @param doctorId  医生主键
 * @param sessionId 会话主键
 */
public record DoctorTokenClaims(long doctorId, long sessionId) {
}
