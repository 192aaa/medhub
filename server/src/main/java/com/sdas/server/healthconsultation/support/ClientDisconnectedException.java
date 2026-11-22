package com.sdas.server.healthconsultation.support;

/**
 * 客户端断开或 SSE 写出失败。
 *
 * <p>由 {@link SseEventWriter} 抛出，Service 据此把助手消息标记为
 * {@code stream_status='INTERRUPTED'} 并停止生成（FR-019）。</p>
 */
public class ClientDisconnectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造异常。
     *
     * @param cause 原始 IO/状态异常
     */
    public ClientDisconnectedException(Throwable cause) {
        super("client disconnected", cause);
    }
}
