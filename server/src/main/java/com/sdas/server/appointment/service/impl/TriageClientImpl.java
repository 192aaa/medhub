package com.sdas.server.appointment.service.impl;

import com.sdas.server.appointment.service.TriageAiResult;
import com.sdas.server.appointment.service.TriageClient;
import com.sdas.server.config.TriageProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * AI 导诊客户端实现：调用 DeepSeek 官方 API，受 {@code sdas.ai.triage.timeout-millis} 约束。
 *
 * <p>超时或异常统一降级为 {@link TriageAiResult#timeout()}，由上层组装全量科室列表（HTTP 200，SC-006）。</p>
 */
@Service
public class TriageClientImpl implements TriageClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TriageClientImpl.class);

    private static final String SYSTEM_PROMPT =
            "你是医院智能导诊助手。请根据患者描述推荐合适的科室名称，只返回科室名称，"
                    + "多个用中文逗号分隔，不要任何解释、不要诊断结论。";

    private final ChatClient chatClient;

    private final long timeoutMillis;

    /**
     * 构造导诊客户端。
     *
     * @param chatClientBuilder ChatClient 构造器（复用 001 AI 自动配置）
     * @param properties        导诊配置
     */
    public TriageClientImpl(ChatClient.Builder chatClientBuilder, TriageProperties properties) {
        this.chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.model())
                        // 推理模型需足够输出预算，否则 content 会被 reasoning 耗尽而为空
                        .maxTokens(1024)
                        .build())
                .build();
        this.timeoutMillis = properties.timeoutMillis();
    }

    @Override
    public TriageAiResult recommend(String complaint) {
        CompletableFuture<List<String>> future = CompletableFuture.supplyAsync(() -> callAi(complaint));
        try {
            return new TriageAiResult(true, future.get(timeoutMillis, TimeUnit.MILLISECONDS));
        } catch (TimeoutException exception) {
            LOGGER.warn("Triage AI timeout after {}ms", timeoutMillis);
            return TriageAiResult.timeout();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TriageAiResult.timeout();
        } catch (ExecutionException exception) {
            LOGGER.warn("Triage AI failed: {}", exception.getCause().getMessage());
            return TriageAiResult.timeout();
        }
    }

    private List<String> callAi(String complaint) {
        String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(complaint)
                .call()
                .content();
        return parseDepartments(content);
    }

    private List<String> parseDepartments(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String part : content.split("[,，、\\n\\s]+")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }
}
