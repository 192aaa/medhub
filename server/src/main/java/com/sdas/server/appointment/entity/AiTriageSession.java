package com.sdas.server.appointment.entity;

import com.sdas.server.appointment.model.TriageResultStatus;
import java.time.LocalDateTime;

/**
 * AI 导诊会话实体（患者可为空，支持匿名导诊）。
 *
 * <p>表结构来源：specs/002-appointment-consultation/data-model.md 2.10。
 * recommended_department_ids 以 JSON 数组字符串存储。</p>
 *
 * @param id                      主键
 * @param patientId              患者（匿名时为空）
 * @param complaint              症状描述
 * @param recommendedDepartmentIds 推荐科室 ID 列表（JSON）
 * @param modelUsed              模型名
 * @param elapsedMs             调用耗时（含超时）
 * @param status                 结果状态
 * @param kbVersion             知识库版本
 * @param createdAt             创建时间
 * @param updatedAt             更新时间
 */
public record AiTriageSession(Long id, Long patientId, String complaint,
                              String recommendedDepartmentIds, String modelUsed, long elapsedMs,
                              TriageResultStatus status, String kbVersion, LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
}
