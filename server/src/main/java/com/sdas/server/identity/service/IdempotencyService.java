package com.sdas.server.identity.service;

import java.util.function.Supplier;

/**
 * 幂等执行器：同一 X-Idempotency-Key 10 分钟内复用首次结果快照（FR-017、SC-008）。
 */
public interface IdempotencyService {

    /**
     * 执行动作；命中快照时直接返回，否则执行并写入快照。
     *
     * @param key     幂等键
     * @param type    结果类型
     * @param action  首次执行的动作
     * @param <T>     结果类型
     * @return 首次结果或快照
     */
    <T> T execute(String key, Class<T> type, Supplier<T> action);
}
