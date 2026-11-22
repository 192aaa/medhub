package com.sdas.server.healthconsultation.support;

/**
 * 模型调用异常（M1 错误语义）。
 *
 * <p>只表达失败原因，不携带任何兜底文案；message 由 Service 依据 {@code ErrorCode} 决定。</p>
 */
public class HealthChatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /**
     * 构造异常。
     *
     * @param reason 失败原因
     * @param cause  原始异常（可为 null）
     */
    public HealthChatException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    /** @return 失败原因 */
    public Reason reason() {
        return reason;
    }

    /** 失败原因分类。 */
    public enum Reason {

        /** 首包或整体超时。 */
        TIMEOUT,

        /** 上游模型/网络错误。 */
        UPSTREAM_ERROR,

        /** 上游返回空内容。 */
        EMPTY
    }
}
