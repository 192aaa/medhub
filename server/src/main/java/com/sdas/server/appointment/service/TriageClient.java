package com.sdas.server.appointment.service;

/** AI 智能导诊客户端（复用 001 的 DeepSeek 官方 API 链路，禁止第二套）。 */
public interface TriageClient {

    /**
     * 根据症状描述推荐科室名称。
     *
     * @param complaint 症状描述
     * @return 推荐结果（超时/异常返回 {@link TriageAiResult#timeout()}）
     */
    TriageAiResult recommend(String complaint);
}
