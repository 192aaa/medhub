package com.sdas.server.doctor.service;

/**
 * 治疗决策模型客户端（plan.md M1）。
 *
 * <p>复用 003 的 Spring AI {@code ChatClient.Builder} 单一链路，同步一次性拉取严格 JSON；
 * 超时/异常/空返回一律降级为 {@link TreatmentDecisionResult}，不抛业务异常。</p>
 */
public interface TreatmentDecisionClient {

    /**
     * 调用模型生成结构化治疗决策。
     *
     * @param command 提示词与模型参数
     * @return 调用结果（失败时 success=false）
     */
    TreatmentDecisionResult decide(TreatmentDecisionCommand command);
}
