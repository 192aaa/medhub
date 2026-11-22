package com.sdas.server.doctor.model;

/**
 * 降级原因（specs/006-ai-treatment-decision/data-model.md 1.3）。
 */
public enum DegradedReason {

    /** 未降级。 */
    NONE,

    /** 超时。 */
    TIMEOUT,

    /** 上游异常。 */
    UPSTREAM_ERROR,

    /** JSON 解析/字段校验失败。 */
    PARSE_FAILED,

    /** 返回空内容。 */
    EMPTY,

    /** 知识库不可用（仍可用模型通用知识，标注依据有限）。 */
    KNOWLEDGE_UNAVAILABLE
}
