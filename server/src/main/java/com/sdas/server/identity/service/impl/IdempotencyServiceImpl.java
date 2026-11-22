package com.sdas.server.identity.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.service.IdempotencyService;
import com.sdas.server.identity.support.RedisKeys;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 幂等实现；Redis 不可用统一降级为 SERVICE_UNAVAILABLE，不产生半成品账户。
 */
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 构造幂等服务。
     *
     * @param redisTemplate String Redis 模板
     * @param objectMapper  JSON 序列化器
     */
    public IdempotencyServiceImpl(StringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T execute(String key, Class<T> type, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }
        String redisKey = RedisKeys.idempotency(key);
        try {
            String snapshot = redisTemplate.opsForValue().get(redisKey);
            if (snapshot != null && !snapshot.isBlank()) {
                return objectMapper.readValue(snapshot, type);
            }
            T result = action.get();
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(result),
                    Duration.ofSeconds(RedisKeys.IDEMPOTENCY_TTL_SECONDS));
            return result;
        } catch (DataAccessException | JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
