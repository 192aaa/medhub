package com.sdas.server.doctor.service.impl;

import com.sdas.server.config.TreatmentDecisionProperties;
import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.service.TreatmentDecisionClient;
import com.sdas.server.doctor.service.TreatmentDecisionCommand;
import com.sdas.server.doctor.service.TreatmentDecisionResult;
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
 * 治疗决策模型客户端实现（plan.md M1）。
 *
 * <p>与 003 使用同一条 DeepSeek/SophNet 链路：{@link CompletableFuture} +
 * {@code get(timeoutMillis)}。异常只映射为 {@link DegradedReason}，日志不打印提示词与模型输出。</p>
 */
@Service
public class TreatmentDecisionClientImpl implements TreatmentDecisionClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TreatmentDecisionClientImpl.class);

    private final ChatClient.Builder chatClientBuilder;

    private final TreatmentDecisionProperties properties;

    /**
     * 构造客户端。
     *
     * @param chatClientBuilder Spring AI 构造器（Boot 自动配置）
     * @param properties        006 配置
     */
    public TreatmentDecisionClientImpl(ChatClient.Builder chatClientBuilder,
                                       TreatmentDecisionProperties properties) {
        this.chatClientBuilder = chatClientBuilder;
        this.properties = properties;
    }

    @Override
    public TreatmentDecisionResult decide(TreatmentDecisionCommand command) {
        String model = command.model() == null || command.model().isBlank()
                ? properties.model() : command.model();
        int maxTokens = command.maxOutputTokens() > 0
                ? command.maxOutputTokens() : properties.maxOutputTokens();
        ChatClient chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .build())
                .build();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> chatClient.prompt()
                        .system(command.systemPrompt())
                        .user(command.userPrompt())
                        .call()
                        .content());
        try {
            String content = future.get(properties.timeoutMillis(), TimeUnit.MILLISECONDS);
            if (content == null || content.isBlank()) {
                return TreatmentDecisionResult.degraded(DegradedReason.EMPTY);
            }
            return TreatmentDecisionResult.success(content, model);
        } catch (TimeoutException exception) {
            LOGGER.warn("Treatment decision timeout after {}ms", properties.timeoutMillis());
            return TreatmentDecisionResult.degraded(DegradedReason.TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TreatmentDecisionResult.degraded(DegradedReason.UPSTREAM_ERROR);
        } catch (ExecutionException exception) {
            LOGGER.warn("Treatment decision failed, reason={}",
                    exception.getCause() == null ? "UNKNOWN"
                            : exception.getCause().getClass().getSimpleName());
            return TreatmentDecisionResult.degraded(DegradedReason.UPSTREAM_ERROR);
        }
    }
}
