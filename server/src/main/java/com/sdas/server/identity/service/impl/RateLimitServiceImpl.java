package com.sdas.server.identity.service.impl;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.service.RateLimitService;
import com.sdas.server.identity.support.RedisKeys;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 限流实现；Redis 不可用统一降级为 SERVICE_UNAVAILABLE（契约 FR-020、FR-024）。
 */
@Service
public class RateLimitServiceImpl implements RateLimitService {

    private static final int LOGIN_FAILURE_LIMIT = 20;

    private static final int VERIFY_ATTEMPT_LIMIT = 20;

    private static final Duration WINDOW = Duration.ofSeconds(900);

    private static final Duration DAILY = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;

    private final IdentityProperties properties;

    /**
     * 构造限流服务。
     *
     * @param redisTemplate String Redis 模板
     * @param properties    患者身份模块配置
     */
    public RateLimitServiceImpl(StringRedisTemplate redisTemplate,
                                IdentityProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void checkSend(String phoneDigest) {
        try {
            Boolean allowed = redisTemplate.opsForValue().setIfAbsent(
                    RedisKeys.rateSend(phoneDigest) + ":interval", "1",
                    Duration.ofSeconds(properties.verificationCode().sendIntervalSeconds()));
            if (!Boolean.TRUE.equals(allowed)) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
            long count = increment(RedisKeys.rateSend(phoneDigest) + ":daily", DAILY);
            if (count > properties.verificationCode().dailyLimit()) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void checkIdentityVerification(String phoneDigest) {
        try {
            long count = increment(RedisKeys.rateVerify(phoneDigest), WINDOW);
            if (count > VERIFY_ATTEMPT_LIMIT) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public boolean shouldSkipPasswordCheck(String phoneDigest) {
        try {
            long count = increment(RedisKeys.rateLogin(phoneDigest), WINDOW);
            return count > LOGIN_FAILURE_LIMIT;
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public long sendIntervalSeconds() {
        return properties.verificationCode().sendIntervalSeconds();
    }

    private long increment(String key, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return count == null ? 0L : count;
    }
}
