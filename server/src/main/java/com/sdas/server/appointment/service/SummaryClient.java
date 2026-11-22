package com.sdas.server.appointment.service;

/** AI 病情摘要客户端：生成问诊单摘要供医生侧参考（不作为诊断结论）。 */
public interface SummaryClient {

    /**
     * 生成病情摘要。
     *
     * @param complaint   主诉
     * @param description 选填描述
     * @return 摘要文本；失败返回 null（不阻断主流程，FR-020）
     */
    String summarize(String complaint, String description);
}
