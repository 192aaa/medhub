package com.sdas.server.identity.service;

/**
 * 从药店 access token 解析出的身份声明。
 *
 * @param pharmacyId 药店账号主键
 * @param sessionId  会话主键（仅用于 Redis 吊销校验，不对外暴露）
 */
public record PharmacyTokenClaims(long pharmacyId, long sessionId) {
}
