package com.sdas.server.healthconsultation.support;

import com.sdas.server.config.HealthProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * {@link HealthChatClient} 默认实现（R-01 / R-03）。
 *
 * <p>复用 002 的 Spring AI {@code ChatClient.Builder}（DeepSeek 官方 API，OpenAI 协议兼容）。
 * 超时策略：{@code timeout(firstTokenTimeoutMillis)} 为 item 间空闲超时（覆盖首包与中途卡死），
 * {@code takeUntilOther(deadline)} 为单轮整体截止；两者任一触发均以 {@code TIMEOUT} 传播。
 * Spring AI 的 OpenAI 客户端为阻塞 HTTP，故订阅切到 {@code boundedElastic}，不占用 Tomcat 线程。</p>
 */
@Component
public class HealthChatClientImpl implements HealthChatClient {

    private final ChatClient chatClient;

    private final HealthProperties properties;

    /**
     * 构造模型客户端。
     *
     * @param chatClientBuilder 既有 ChatClient 构建器（002 自动配置）
     * @param properties        005 配置
     */
    public HealthChatClientImpl(ChatClient.Builder chatClientBuilder, HealthProperties properties) {
        this.properties = properties;
        this.chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.model())
                        .maxTokens(properties.maxOutputTokens())
                        .build())
                .build();
    }

    @Override
    public Flux<String> stream(HealthChatCommand command) {
        List<Message> messages = command.turns().stream()
                .map(turn -> turn.assistant()
                        ? (Message) new AssistantMessage(turn.content())
                        : (Message) new UserMessage(turn.content()))
                .toList();

        Mono<Object> deadline = Mono.delay(Duration.ofMillis(properties.timeoutMillis()))
                .flatMap(ignored -> Mono.error(new HealthChatException(
                        HealthChatException.Reason.TIMEOUT, null)));

        return chatClient.prompt()
                .system(command.systemPrompt())
                .messages(messages)
                .stream()
                .content()
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMillis(properties.firstTokenTimeoutMillis()))
                .takeUntilOther(deadline)
                .onErrorMap(TimeoutException.class,
                        ex -> new HealthChatException(HealthChatException.Reason.TIMEOUT, ex))
                .onErrorMap((Throwable ex) -> !(ex instanceof HealthChatException),
                        ex -> new HealthChatException(
                                HealthChatException.Reason.UPSTREAM_ERROR, ex))
                .switchIfEmpty(Flux.error(new HealthChatException(
                        HealthChatException.Reason.EMPTY, null)));
    }
}
