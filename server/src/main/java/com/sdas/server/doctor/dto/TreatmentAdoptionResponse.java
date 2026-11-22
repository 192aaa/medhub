package com.sdas.server.doctor.dto;

/**
 * 端点 4 响应 data。
 *
 * @param decisionId       被回填的留痕主键
 * @param appliedItemCount 采纳条数
 */
public record TreatmentAdoptionResponse(long decisionId, int appliedItemCount) {
}
