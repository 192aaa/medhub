package com.sdas.server.healthconsultation.model;

/**
 * 流式完成状态。
 *
 * <p>枚举唯一来源：specs/005-ai-health-consultation/data-model.md 1.3。
 * 仅 {@code COMPLETED} 的消息进入下一轮上下文（R-06 热窗口）。</p>
 */
public enum StreamStatus {

    /** 正常结束，正文完整。 */
    COMPLETED,

    /** 模型/网络失败，正文可能为空或部分。 */
    FAILED,

    /** 客户端断开或患者主动停止，保留已生成内容。 */
    INTERRUPTED
}
