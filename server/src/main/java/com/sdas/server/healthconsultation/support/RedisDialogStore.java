package com.sdas.server.healthconsultation.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 多轮上下文热窗口存储（Redis 3.2.100，R-06）。
 *
 * <p>键规范见 specs/005-ai-health-consultation/data-model.md 第 4 节：
 * {@code ai:health:ctx:{sid}}（List）、{@code ai:health:meta:{sid}}（Hash）、
 * {@code ai:health:lock:{sid}}（String）。</p>
 *
 * <p><b>只使用</b> String/List/Hash 与 {@code EXPIRE}/{@code LTRIM}/{@code WATCH}/{@code MULTI}；
 * 禁止 Streams、{@code XAUTOCLAIM}、{@code UNLINK}。Redis 不可用时全部方法降级为
 * 「返回安全默认值 + WARN」，由调用方改走「每轮从 MySQL 重建」，对话不中断。</p>
 */
@Component
public class RedisDialogStore {

    private static final Logger LOG = LoggerFactory.getLogger(RedisDialogStore.class);

    private static final String CTX_PREFIX = "ai:health:ctx:";

    private static final String META_PREFIX = "ai:health:meta:";

    private static final String LOCK_PREFIX = "ai:health:lock:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造热窗口存储。
     *
     * @param redisTemplate 字符串模板（Boot 自动配置）
     */
    public RedisDialogStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取会话级生成锁（SET NX EX）。
     *
     * <p>区分三种结果：拿到锁、锁被占用、Redis 不可用（降级为无锁放行，保证对话不中断）。
     * 降级时 {@code storeAvailable=false} 且 {@code acquired=true}，令牌为 {@code null}。</p>
     *
     * @param sessionId  会话主键
     * @param ttlSeconds 锁有效期（秒）
     * @return 加锁结果
     */
    public LockOutcome lock(long sessionId, int ttlSeconds) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_PREFIX + sessionId, token, Duration.ofSeconds(ttlSeconds));
            return Boolean.TRUE.equals(ok)
                    ? new LockOutcome(token, true, true)
                    : new LockOutcome(null, false, true);
        } catch (DataAccessException ex) {
            LOG.warn("redis lock unavailable, sessionId={}, reason={}", sessionId,
                    ex.getClass().getSimpleName());
            return new LockOutcome(null, true, false);
        }
    }

    /**
     * 加锁结果。
     *
     * @param token          锁令牌（降级或未获取时为 null）
     * @param acquired       是否可继续生成（降级时也放行）
     * @param storeAvailable Redis 是否可用
     */
    public record LockOutcome(String token, boolean acquired, boolean storeAvailable) {
    }

    /**
     * 释放会话级生成锁（WATCH/MULTI 校验令牌值，避免误删他人锁）。
     *
     * @param sessionId 会话主键
     * @param token     加锁时返回的令牌
     */
    public void unlock(long sessionId, String token) {
        if (token == null) {
            return;
        }
        try {
            redisTemplate.execute(new SessionCallback<Integer>() {
                @SuppressWarnings({"unchecked", "rawtypes"})
                @Override
                public Integer execute(RedisOperations operations) {
                    String key = LOCK_PREFIX + sessionId;
                    operations.watch(key);
                    Object current = operations.opsForValue().get(key);
                    if (!token.equals(current)) {
                        operations.unwatch();
                        return 0;
                    }
                    operations.multi();
                    operations.delete(key);
                    operations.exec();
                    return 1;
                }
            });
        } catch (DataAccessException ex) {
            LOG.warn("redis unlock failed, sessionId={}, reason={}", sessionId,
                    ex.getClass().getSimpleName());
        }
    }

    /**
     * 读取热窗口（正序）。
     *
     * @param sessionId 会话主键
     * @return 窗口条目；Redis 不可用时返回空列表
     */
    public List<String> window(long sessionId) {
        try {
            List<String> entries = redisTemplate.opsForList().range(CTX_PREFIX + sessionId, 0, -1);
            return entries == null ? Collections.emptyList() : entries;
        } catch (DataAccessException ex) {
            LOG.warn("redis window unavailable, sessionId={}, reason={}", sessionId,
                    ex.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    /**
     * 追加窗口条目并裁剪、续期。
     *
     * @param sessionId 会话主键
     * @param entries   待追加条目（{@code ROLE|content}）
     * @param maxSize   窗口容量
     * @param ttl       过期时间
     */
    public void appendWindow(long sessionId, List<String> entries, int maxSize, Duration ttl) {
        if (entries.isEmpty()) {
            return;
        }
        String key = CTX_PREFIX + sessionId;
        try {
            redisTemplate.opsForList().rightPushAll(key, entries);
            redisTemplate.opsForList().trim(key, -maxSize, -1);
            redisTemplate.expire(key, ttl);
        } catch (DataAccessException ex) {
            LOG.warn("redis window append failed, sessionId={}, reason={}", sessionId,
                    ex.getClass().getSimpleName());
        }
    }

    /**
     * 用重建结果覆盖窗口（miss 回填）。
     *
     * @param sessionId 会话主键
     * @param entries   重建条目（正序）
     * @param maxSize   窗口容量
     * @param ttl       过期时间
     */
    public void rebuildWindow(long sessionId, List<String> entries, int maxSize, Duration ttl) {
        String key = CTX_PREFIX + sessionId;
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException ex) {
            LOG.warn("redis window reset failed, sessionId={}, reason={}", sessionId,
                    ex.getClass().getSimpleName());
            return;
        }
        appendWindow(sessionId, entries, maxSize, ttl);
    }

    /**
     * 读取会话热元信息。
     *
     * @param sessionId 会话主键
     * @return 元信息；不存在或 Redis 不可用时返回 {@code null}
     */
    public DialogMeta meta(long sessionId) {
        try {
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(META_PREFIX + sessionId);
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            return new DialogMeta(
                    Long.parseLong(String.valueOf(raw.getOrDefault("patientId", "0"))),
                    String.valueOf(raw.getOrDefault("title", "")),
                    String.valueOf(raw.getOrDefault("lastActiveAt", "")),
                    Integer.parseInt(String.valueOf(raw.getOrDefault("messageCount", "0"))));
        } catch (DataAccessException | NumberFormatException ex) {
            LOG.warn("redis meta unavailable, sessionId={}, reason={}", sessionId,
                    ex.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 写入会话热元信息。
     *
     * @param sessionId 会话主键
     * @param meta      元信息
     * @param ttl       过期时间
     */
    public void saveMeta(long sessionId, DialogMeta meta, Duration ttl) {
        String key = META_PREFIX + sessionId;
        try {
            Map<String, String> values = Map.of(
                    "patientId", String.valueOf(meta.patientId()),
                    "title", meta.title() == null ? "" : meta.title(),
                    "lastActiveAt", meta.lastActiveAt() == null ? "" : meta.lastActiveAt(),
                    "messageCount", String.valueOf(meta.messageCount()));
            redisTemplate.opsForHash().putAll(key, values);
            redisTemplate.expire(key, ttl);
        } catch (DataAccessException ex) {
            LOG.warn("redis meta save failed, sessionId={}, reason={}", sessionId,
                    ex.getClass().getSimpleName());
        }
    }

    /**
     * 删除会话的热窗口、元信息与锁。
     *
     * @param sessionId 会话主键
     */
    public void deleteAll(long sessionId) {
        List<String> keys = new ArrayList<>(3);
        keys.add(CTX_PREFIX + sessionId);
        keys.add(META_PREFIX + sessionId);
        keys.add(LOCK_PREFIX + sessionId);
        try {
            redisTemplate.delete(keys);
        } catch (DataAccessException ex) {
            LOG.warn("redis delete failed, sessionId={}, reason={}", sessionId,
                    ex.getClass().getSimpleName());
        }
    }
}
