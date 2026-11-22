package com.sdas.server.doctor.service;

import java.util.List;

/**
 * AI 辅助决策结果。
 *
 * @param success     是否成功返回建议
 * @param suggestions 辅助参考条目
 * @param modelUsed   模型名
 */
public record AiDecisionResult(boolean success, List<String> suggestions, String modelUsed) {

    /** 防御性拷贝：建议列表以不可变副本保存，避免调用方回写内部状态。 */
    public AiDecisionResult {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    /** 超时 / 异常降级结果（不阻塞接诊开方）。 */
    public static AiDecisionResult timeout() {
        return new AiDecisionResult(false, List.of(), null);
    }
}
