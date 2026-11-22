package com.sdas.server.doctor.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.config.TreatmentDecisionProperties;
import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.doctor.service.TreatmentDecisionCommand;
import com.sdas.server.doctor.service.TreatmentDecisionResult;
import com.sdas.server.doctor.service.impl.TreatmentDecisionClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

/** 006 模型客户端：正常透传、超时、上游异常、空内容，且不产生兜底文案。 */
class TreatmentDecisionClientTest {

    private final ChatClient chatClient = mock(ChatClient.class);

    private final ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class);

    private final ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

    private ChatClient.Builder builder;

    private TreatmentDecisionProperties properties;

    @BeforeEach
    void setUp() {
        builder = mock(ChatClient.Builder.class);
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        properties = properties(2000L);
    }

    @Test
    @DisplayName("正常 JSON 原样透传")
    void passesThroughContent() {
        when(callSpec.content()).thenReturn("{\"summary\":{}}");
        TreatmentDecisionClientImpl client = new TreatmentDecisionClientImpl(builder, properties);

        TreatmentDecisionResult result = client.decide(command());

        assertThat(result.success()).isTrue();
        assertThat(result.rawJson()).isEqualTo("{\"summary\":{}}");
        assertThat(result.modelUsed()).isEqualTo("test-model");
        assertThat(result.reason()).isEqualTo(DegradedReason.NONE);
    }

    @Test
    @DisplayName("get 超时映射为 TIMEOUT 且不生成兜底文案")
    void timeoutMapsToTimeout() {
        when(callSpec.content()).thenAnswer(invocation -> {
            Thread.sleep(300L);
            return "{\"summary\":{}}";
        });
        TreatmentDecisionClientImpl client =
                new TreatmentDecisionClientImpl(builder, properties(50L));

        TreatmentDecisionResult result = client.decide(command());

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).isEqualTo(DegradedReason.TIMEOUT);
        assertThat(result.rawJson()).isNull();
    }

    @Test
    @DisplayName("上游异常映射为 UPSTREAM_ERROR")
    void upstreamErrorMaps() {
        when(callSpec.content()).thenThrow(new IllegalStateException("boom"));
        TreatmentDecisionClientImpl client = new TreatmentDecisionClientImpl(builder, properties);

        TreatmentDecisionResult result = client.decide(command());

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).isEqualTo(DegradedReason.UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("空白内容映射为 EMPTY")
    void blankContentMapsToEmpty() {
        when(callSpec.content()).thenReturn("   ");
        TreatmentDecisionClientImpl client = new TreatmentDecisionClientImpl(builder, properties);

        TreatmentDecisionResult result = client.decide(command());

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).isEqualTo(DegradedReason.EMPTY);
    }

    private static TreatmentDecisionCommand command() {
        return new TreatmentDecisionCommand("system", "user", 512, "test-model");
    }

    private static TreatmentDecisionProperties properties(long timeoutMillis) {
        return new TreatmentDecisionProperties(null, timeoutMillis, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
