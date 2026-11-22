package com.sdas.server.doctor.dto;

import jakarta.validation.constraints.Size;

/**
 * 端点 9 请求体（AI 辅助决策）。
 *
 * @param query 医生附加提问（可选）
 */
public record AiDecisionRequest(@Size(max = 500) String query) {
}
