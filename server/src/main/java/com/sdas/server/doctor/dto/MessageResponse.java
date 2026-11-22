package com.sdas.server.doctor.dto;

import java.time.LocalDateTime;

/**
 * 端点 6 响应 data（发送回复回显）。
 *
 * @param messageId  消息主键
 * @param senderType 发送方（DOCTOR）
 * @param content    回复内容
 * @param createdAt  发送时间
 */
public record MessageResponse(long messageId, String senderType, String content,
                              LocalDateTime createdAt) {
}
