package com.sdas.server.appointment.entity;

import java.time.LocalDateTime;

/**
 * 科室-疾病知识库实体（数据库预置，无管理界面）。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.4。</p>
 *
 * @param id             主键
 * @param departmentId   关联科室
 * @param diseaseKeyword 疾病关键词（用于关键词兜底匹配）
 * @param diseaseName    疾病名
 * @param source         来源（PRESET）
 * @param kbVersion     知识库版本
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 */
public record DepartmentDiseaseKb(Long id, Long departmentId, String diseaseKeyword,
                                  String diseaseName, String source, String kbVersion,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
}
