package com.sdas.server.health.repository;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis 持久化探针，仅负责执行 {@code PING}。
 *
 * <p>使用 Spring Boot 自动配置的 StringRedisTemplate，避免对象序列化影响 PING。</p>
 */
@Repository
public class RedisProbeRepository {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造 Redis 探针。
     *
     * @param stringRedisTemplate 字符串 Redis 模板
     */
    public RedisProbeRepository(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 执行 PING 命令。
     *
     * @return 正常为 PONG
     */
    public String ping() {
        return stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
    }
}
