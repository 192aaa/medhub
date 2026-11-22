package com.sdas.server.doctor.dto;

import java.util.List;

/**
 * 端点 9 响应 data（AI 辅助决策）。
 *
 * @param decisionId  决策会话主键
 * @param suggestions 辅助参考条目
 * @param disclaimer  固定声明「辅助参考，不作为诊断结论」
 * @param modelUsed   模型名
 * @param status      结果状态（SUCCESS/TIMEOUT_FALLBACK）
 */
public record AiDecisionResponse(
        long decisionId,
        List<String> suggestions,
        String disclaimer,
        String modelUsed,
        String status) {
}
