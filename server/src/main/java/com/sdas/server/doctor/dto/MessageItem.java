package com.sdas.server.doctor.dto;

import java.time.LocalDateTime;

/**
 * 端点 5 响应元素（对话消息）。
 *
 * @param messageId  消息主键
 * @param senderType 发送方（DOCTOR/PATIENT/SYSTEM）
 * @param content    内容
 * @param createdAt  发送时间
 */
public record MessageItem(long messageId, String senderType, String content,
                          LocalDateTime createdAt) {
}
