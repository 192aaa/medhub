package com.sdas.server.doctor.service.impl;

import com.sdas.server.config.DecisionProperties;
import com.sdas.server.doctor.service.AiDecisionClient;
import com.sdas.server.doctor.service.AiDecisionResult;
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
 * AI 辅助决策客户端实现：复用 002 DeepSeek/SophNet 链路，受 {@code sdas.ai.decision.timeout-millis} 约束。
 *
 * <p>超时或异常统一降级为 {@link AiDecisionResult#timeout()}，不阻塞接诊与开方（SC-009）。</p>
 */
@Service
public class AiDecisionClientImpl implements AiDecisionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiDecisionClientImpl.class);

    private static final String SYSTEM_PROMPT =
            "你是医院辅助决策助手。请基于问诊资料与药品目录给出疑似疾病参考与用药配伍/禁忌提醒，"
                    + "每条一行，不要给出诊断结论，不要任何解释性文字。";

    private final ChatClient chatClient;

    private final long timeoutMillis;

    private final String model;

    /**
     * 构造辅助决策客户端。
     *
     * @param chatClientBuilder ChatClient 构造器（复用 002 AI 自动配置）
     * @param properties        辅助决策配置
     */
    public AiDecisionClientImpl(ChatClient.Builder chatClientBuilder, DecisionProperties properties) {
        this.chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.model())
                        .maxTokens(1024)
                        .build())
                .build();
        this.timeoutMillis = properties.timeoutMillis();
        this.model = properties.model();
    }

    @Override
    public AiDecisionResult decide(String prompt) {
        CompletableFuture<List<String>> future = CompletableFuture.supplyAsync(() -> callAi(prompt));
        try {
            List<String> suggestions = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return new AiDecisionResult(true, suggestions, model);
        } catch (TimeoutException exception) {
            LOGGER.warn("Decision AI timeout after {}ms", timeoutMillis);
            return AiDecisionResult.timeout();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AiDecisionResult.timeout();
        } catch (ExecutionException exception) {
            LOGGER.warn("Decision AI failed: {}", String.valueOf(exception.getCause()));
            return AiDecisionResult.timeout();
        }
    }

    private List<String> callAi(String prompt) {
        String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(prompt)
                .call()
                .content();
        return parseSuggestions(content);
    }

    private List<String> parseSuggestions(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        for (String line : content.split("\\r?\\n")) {
            String item = line.replaceFirst("^[-•*\\d.、\\s]+", "").trim();
            if (!item.isEmpty()) {
                suggestions.add(item);
            }
        }
        return suggestions;
    }
}
