package com.sdas.server.healthconsultation.support;

/**
 * 会话热元信息（Redis Hash {@code ai:health:meta:{sessionId}}）。
 *
 * @param patientId    归属患者
 * @param title        标题
 * @param lastActiveAt 最近活跃时间（ISO 8601 字符串）
 * @param messageCount 消息条数
 */
public record DialogMeta(long patientId, String title, String lastActiveAt, int messageCount) {
}
