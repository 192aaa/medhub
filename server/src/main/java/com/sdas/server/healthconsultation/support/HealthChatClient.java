package com.sdas.server.healthconsultation.support;

import reactor.core.publisher.Flux;

/**
 * 模型调用唯一出口（R-03）。
 *
 * <p>Spring AI 类型不得越过本接口；实现只负责流式生成与错误归类，
 * <b>不生成任何兜底文案</b>，是否发 {@code error} 事件、如何标记
 * {@code stream_status} 由 Service 决定（FR-020）。</p>
 */
public interface HealthChatClient {

    /**
     * 流式生成回答。
     *
     * @param command 提示词、历史轮次、模型与输出上限
     * @return 正文增量流；错误以 {@link HealthChatException} 传播
     */
    Flux<String> stream(HealthChatCommand command);
}
