package com.sdas.server.healthconsultation.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.healthconsultation.dto.HealthSseEvents;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 事件写出（唯一的协议出口）。
 *
 * <p>事件名与载荷字段严格对齐 {@code contracts/ai-health-consultation-api.md}；
 * 响应头关闭代理缓冲（{@code X-Accel-Buffering: no}），否则会出现「一次性返回」。
 * 写出失败视为客户端断开，抛 {@link ClientDisconnectedException} 由 Service 标记中断。</p>
 */
@Component
public class SseEventWriter {

    private final ObjectMapper objectMapper;

    /**
     * 构造 SSE 写出器。
     *
     * @param objectMapper 既有 Jackson 配置
     */
    public SseEventWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 设置 SSE 响应头。
     *
     * @param response 响应
     */
    public void prepare(HttpServletResponse response) {
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
    }

    /**
     * 发送 accepted 事件。
     *
     * @param emitter   SSE 发射器
     * @param messageId 患者消息主键
     * @param sessionId 会话主键
     */
    public void accepted(SseEmitter emitter, long messageId, long sessionId) {
        send(emitter, "accepted", new HealthSseEvents.Accepted(messageId, sessionId));
    }

    /**
     * 发送 emergency 事件（必须先于任何 delta）。
     *
     * @param emitter   SSE 发射器
     * @param emergency 预警载荷
     */
    public void emergency(SseEmitter emitter, HealthSseEvents.Emergency emergency) {
        send(emitter, "emergency", emergency);
    }

    /**
     * 发送正文增量。
     *
     * @param emitter SSE 发射器
     * @param text    增量文本
     */
    public void delta(SseEmitter emitter, String text) {
        send(emitter, "delta", new HealthSseEvents.Delta(text));
    }

    /**
     * 发送免责声明（文案为服务端常量）。
     *
     * @param emitter SSE 发射器
     */
    public void disclaimer(SseEmitter emitter) {
        send(emitter, "disclaimer", new HealthSseEvents.Disclaimer(HealthDisclaimer.TEXT));
    }

    /**
     * 发送 done 事件。
     *
     * @param emitter   SSE 发射器
     * @param messageId 助手消息主键
     * @param truncated 上下文是否截断
     * @param modelUsed 实际模型名
     */
    public void done(SseEmitter emitter, long messageId, boolean truncated, String modelUsed) {
        send(emitter, "done", new HealthSseEvents.Done(messageId, truncated, modelUsed));
    }

    /**
     * 发送 error 事件（与 done 互斥）。
     *
     * @param emitter   SSE 发射器
     * @param errorCode 错误码（message 取自枚举）
     * @param retryable 是否可重试
     */
    public void error(SseEmitter emitter, ErrorCode errorCode, boolean retryable) {
        send(emitter, "error", new HealthSseEvents.Error(errorCode.getCode(),
                errorCode.getMessage(), retryable));
    }

    /**
     * 正常完成流。
     *
     * @param emitter SSE 发射器
     */
    public void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ex) {
            throw new ClientDisconnectedException(ex);
        }
    }

    private void send(SseEmitter emitter, String event, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(event).data(json, MediaType.TEXT_PLAIN));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("sse payload serialization failed", ex);
        } catch (IOException | IllegalStateException ex) {
            throw new ClientDisconnectedException(ex);
        }
    }
}
