package com.sdas.server.appointment.service.impl;

import com.sdas.server.appointment.service.SummaryClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * AI 摘要客户端实现：问诊单提交时生成摘要；超时/异常返回 null，不阻断提交（FR-014/FR-020）。
 */
@Service
public class SummaryClientImpl implements SummaryClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SummaryClientImpl.class);

    private static final long TIMEOUT_MILLIS = 8000;

    private static final String SYSTEM_PROMPT =
            "你是临床助理。请基于患者主诉与描述生成一句简洁的病情摘要供接诊医生参考，"
                    + "仅陈述事实，不要给出诊断结论或用药建议。";

    private final ChatClient chatClient;

    /**
     * 构造摘要客户端。
     *
     * @param chatClientBuilder ChatClient 构造器
     */
    public SummaryClientImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String summarize(String complaint, String description) {
        String prompt = "主诉：" + (complaint == null ? "" : complaint)
                + "；描述：" + (description == null ? "" : description);
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> chatClient.prompt()
                .system(SYSTEM_PROMPT).user(prompt).call().content());
        try {
            return future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            LOGGER.warn("Summary AI timeout");
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException exception) {
            LOGGER.warn("Summary AI failed: {}", exception.getCause().getMessage());
            return null;
        }
    }
}
