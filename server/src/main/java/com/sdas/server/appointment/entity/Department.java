package com.sdas.server.appointment.entity;

import java.time.LocalDateTime;

/**
 * 科室实体。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.1。</p>
 *
 * @param id          主键
 * @param name        科室名
 * @param description 简介
 * @param iconUrl     图标 URL
 * @param sortOrder   展示排序
 * @param enabled     是否启用
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record Department(Long id, String name, String description, String iconUrl, int sortOrder,
                         boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
