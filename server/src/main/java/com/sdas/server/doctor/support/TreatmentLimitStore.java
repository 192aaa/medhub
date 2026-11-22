package com.sdas.server.doctor.support;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.TreatmentDecisionProperties;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 治疗决策并发锁与频控（plan.md M4，Redis 3.2.100）。
 *
 * <p>键：{@code ai:treatment:lock:{cid}}（{@code SET NX EX 30}）与
 * {@code ai:treatment:rate:{cid}}（{@code INCR} + 首次 {@code EXPIRE 3600}）。
 * 释放锁用 {@code WATCH/MULTI} 校验令牌，禁止 {@code UNLINK}/Lua。
 * Redis 不可用时锁与频控<b>一律降级放行</b>并只记 {@code consultationId} 与异常类名——
 * 任何情况下都不能因为缓存故障阻断医生开方。</p>
 */
@Component
public class TreatmentLimitStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreatmentLimitStore.class);

    private static final String LOCK_PREFIX = "ai:treatment:lock:";

    private static final String RATE_PREFIX = "ai:treatment:rate:";

    private static final int LOCK_TTL_SECONDS = 30;

    private static final int RATE_TTL_SECONDS = 3600;

    private final StringRedisTemplate redisTemplate;

    private final TreatmentDecisionProperties properties;

    /**
     * 构造限流存储。
     *
     * @param redisTemplate 字符串模板
     * @param properties    006 配置（重新生成上限）
     */
    public TreatmentLimitStore(StringRedisTemplate redisTemplate,
                               TreatmentDecisionProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 获取问诊单级生成锁。
     *
     * @param consultationId 问诊单主键
     * @return 加锁结果（Redis 不可用时 {@code acquired=true}、{@code token=null}）
     */
    public LockOutcome lock(long consultationId) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + consultationId,
                    token, Duration.ofSeconds(LOCK_TTL_SECONDS));
            return Boolean.TRUE.equals(ok)
                    ? new LockOutcome(token, true, true)
                    : new LockOutcome(null, false, true);
        } catch (DataAccessException exception) {
            LOGGER.warn("treatment lock unavailable, consultationId={}, reason={}",
                    consultationId, exception.getClass().getSimpleName());
            return new LockOutcome(null, true, false);
        }
    }

    /**
     * 重新生成频控（仅 REGENERATE 调用）。
     *
     * @param consultationId 问诊单主键
     * @throws BusinessException 超过每小时上限时抛 {@code TREATMENT_DECISION_RATE_LIMITED}
     */
    public void checkRegenerate(long consultationId) {
        String key = RATE_PREFIX + consultationId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(RATE_TTL_SECONDS));
            }
            if (count != null && count > properties.regenerateLimitPerHour()) {
                throw new BusinessException(ErrorCode.TREATMENT_DECISION_RATE_LIMITED);
            }
        } catch (DataAccessException exception) {
            LOGGER.warn("treatment rate limit unavailable, consultationId={}, reason={}",
                    consultationId, exception.getClass().getSimpleName());
        }
    }

    /**
     * 释放生成锁（校验令牌，避免误删他人锁）。
     *
     * @param consultationId 问诊单主键
     * @param token          加锁令牌
     */
    public void unlock(long consultationId, String token) {
        if (token == null) {
            return;
        }
        try {
            redisTemplate.execute(new SessionCallback<Integer>() {
                @SuppressWarnings({"unchecked", "rawtypes"})
                @Override
                public Integer execute(RedisOperations operations) {
                    String key = LOCK_PREFIX + consultationId;
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
        } catch (DataAccessException exception) {
            LOGGER.warn("treatment unlock failed, consultationId={}, reason={}",
                    consultationId, exception.getClass().getSimpleName());
        }
    }

    /**
     * 加锁结果。
     *
     * @param token          锁令牌（降级或未获取时为 null）
     * @param acquired       是否可继续生成
     * @param storeAvailable Redis 是否可用
     */
    public record LockOutcome(String token, boolean acquired, boolean storeAvailable) {
    }
}
