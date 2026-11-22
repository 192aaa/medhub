package com.sdas.server.healthconsultation.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.config.HealthProperties;
import com.sdas.server.healthconsultation.support.HealthChatClientImpl;
import com.sdas.server.healthconsultation.support.HealthChatCommand;
import com.sdas.server.healthconsultation.support.HealthChatException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/** 模型封装：正常增量、上游失败、空流（不产生兜底文案）。 */
class HealthChatClientImplTest {

    private final ChatClient chatClient = mock(ChatClient.class);

    private final ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class);

    private final ChatClient.StreamResponseSpec streamSpec =
            mock(ChatClient.StreamResponseSpec.class);

    private HealthChatClientImpl client;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        client = new HealthChatClientImpl(builder, properties());
    }

    @Test
    @DisplayName("正常增量按序透传")
    void streamsChunks() {
        when(streamSpec.content()).thenReturn(Flux.just("第一段", "第二段"));

        List<String> chunks = client.stream(command()).collectList().block();

        assertThat(chunks).containsExactly("第一段", "第二段");
    }

    @Test
    @DisplayName("上游错误映射为 UPSTREAM_ERROR")
    void mapsUpstreamError() {
        when(streamSpec.content()).thenReturn(Flux.error(new IllegalStateException("boom")));

        assertThatThrownBy(() -> client.stream(command()).blockLast())
                .isInstanceOf(HealthChatException.class)
                .satisfies(error -> assertThat(((HealthChatException) error).reason())
                        .isEqualTo(HealthChatException.Reason.UPSTREAM_ERROR));
    }

    @Test
    @DisplayName("空流映射为 EMPTY")
    void emptyStream() {
        when(streamSpec.content()).thenReturn(Flux.empty());

        assertThatThrownBy(() -> client.stream(command()).blockLast())
                .isInstanceOf(HealthChatException.class)
                .satisfies(error -> assertThat(((HealthChatException) error).reason())
                        .isEqualTo(HealthChatException.Reason.EMPTY));
    }

    private static HealthChatCommand command() {
        return new HealthChatCommand("system", List.of(), 1024, "deepseek-v4-flash");
    }

    private static HealthProperties properties() {
        return new HealthProperties("deepseek-v4-flash", 2000, 1000, 1024, 6000, 1500, 12, 2000,
                200, 5);
    }
}
