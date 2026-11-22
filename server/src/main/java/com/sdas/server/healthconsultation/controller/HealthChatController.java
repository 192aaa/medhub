package com.sdas.server.healthconsultation.controller;

import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.healthconsultation.dto.ChatRequest;
import com.sdas.server.healthconsultation.service.HealthChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式问答端点（契约端点 7）。
 *
 * <p>返回 {@code text/event-stream}，<b>不使用</b>统一响应包裹；流建立前失败
 * （401/403/404/409/400/503）仍由 {@code GlobalExceptionHandler} 返回 JSON 统一响应。</p>
 */
@RestController
@RequestMapping("/api/v1/health-consultation/sessions")
public class HealthChatController {

    private final HealthChatService chatService;

    private final PatientIdExtractor patientIdExtractor;

    /**
     * 构造问答控制器。
     *
     * @param chatService        流式问答服务
     * @param patientIdExtractor 患者身份提取器
     */
    public HealthChatController(HealthChatService chatService,
                                PatientIdExtractor patientIdExtractor) {
        this.chatService = chatService;
        this.patientIdExtractor = patientIdExtractor;
    }

    /**
     * 发送患者消息并流式返回回答。
     *
     * @param sessionId   会话主键
     * @param request     请求体（{@code content}）
     * @param httpRequest  请求
     * @param httpResponse 响应（用于 SSE 头）
     * @return SSE 发射器
     */
    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable long sessionId,
                           @Valid @RequestBody ChatRequest request,
                           HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return chatService.chat(patientId, sessionId, request, httpResponse);
    }
}
