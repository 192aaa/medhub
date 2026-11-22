package com.sdas.server.healthconsultation.service;

import com.sdas.server.healthconsultation.dto.ChatRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式问答（SSE）编排。
 *
 * <p>契约：{@code contracts/ai-health-consultation-api.md} 端点 7。
 * 同步阶段完成归属/长度/条数校验与并发锁（失败返回 JSON 统一响应），
 * 异步阶段完成上下文组装、模型流式调用与事件写出。</p>
 */
public interface HealthChatService {

    /**
     * 处理一次流式问答。
     *
     * @param patientId 患者主键（来自认证上下文）
     * @param sessionId 会话主键
     * @param request   请求体
     * @param response  用于设置 SSE 响应头
     * @return SSE 发射器
     */
    SseEmitter chat(long patientId, long sessionId, ChatRequest request,
                    HttpServletResponse response);
}
