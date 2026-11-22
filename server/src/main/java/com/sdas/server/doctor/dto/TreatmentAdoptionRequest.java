package com.sdas.server.doctor.dto;

/**
 * 端点 4 请求体（回填采纳数量）。
 *
 * <p>{@code appliedItemCount} 用包装类型以区分「缺省」与 {@code 0}，
 * 缺省或越界统一抛 {@code INVALID_TREATMENT_ADOPTION}。</p>
 *
 * @param appliedItemCount 采纳条数（0–20）
 */
public record TreatmentAdoptionRequest(Integer appliedItemCount) {
}
