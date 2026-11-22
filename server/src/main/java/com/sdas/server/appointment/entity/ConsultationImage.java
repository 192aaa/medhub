package com.sdas.server.appointment.entity;

import java.time.LocalDateTime;

/**
 * 问诊图片实体（仅存 URL，不存二进制到数据库）。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.9。</p>
 *
 * @param id          主键
 * @param consultationId 关联问诊单
 * @param imageUrl   访问 URL（由 ImageStorageService 生成）
 * @param contentType 校验后内容类型
 * @param sizeBytes  字节大小
 * @param createdAt  创建时间
 * @param updatedAt  更新时间
 */
public record ConsultationImage(Long id, Long consultationId, String imageUrl, String contentType,
                                long sizeBytes, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
