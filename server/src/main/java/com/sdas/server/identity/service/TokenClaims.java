package com.sdas.server.identity.service;

/**
 * 从 access token 解析出的身份声明。
 *
 * @param patientId 患者主键
 * @param sessionId 会话主键
 */
public record TokenClaims(long patientId, long sessionId) {
}
