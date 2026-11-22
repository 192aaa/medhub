package com.sdas.server.doctor.service;

/**
 * AI 辅助决策客户端（复用 002 Spring AI + DeepSeek/SophNet 链路，禁止双链路）。
 */
public interface AiDecisionClient {

    /**
     * 根据问诊资料与药品目录给出辅助参考（疑似疾病 / 用药配伍禁忌）。
     *
     * @param prompt 提示词（含脱敏问诊资料）
     * @return 决策结果（超时/异常时 success=false）
     */
    AiDecisionResult decide(String prompt);
}
