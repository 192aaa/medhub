package com.sdas.server.identity.support;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.model.VerificationCodeStatus;
import com.sdas.server.identity.model.VerificationPurpose;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 验证码 Redis 生命周期：只存 HMAC 摘要，TTL 300 s，原子「校验并置 USED」。
 *
 * <p>键模板：sdas:identity:code:{purpose}:{phoneDigest}（data-model.md 第 4 节）。</p>
 */
@Component
public class VerificationCodeStore {

    private static final RedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local raw = redis.call('GET', KEYS[1]) "
                    + "if not raw then return 0 end "
                    + "local data = cjson.decode(raw) "
                    + "if data.status ~= 'ACTIVE' then return 0 end "
                    + "if data.codeDigest ~= ARGV[1] then return -1 end "
                    + "data.status = 'USED' "
                    + "local ttl = redis.call('PTTL', KEYS[1]) "
                    + "redis.call('SET', KEYS[1], cjson.encode(data)) "
                    + "if ttl > 0 then redis.call('PEXPIRE', KEYS[1], ttl) end "
                    + "return 1", Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造验证码存储。
     *
     * @param redisTemplate String Redis 模板
     */
    public VerificationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 写入（或覆盖）验证码摘要。
     *
     * @param purpose     用途
     * @param phoneDigest 手机号摘要
     * @param codeDigest  验证码摘要
     * @param attempts    尝试次数
     */
    public void save(VerificationPurpose purpose, String phoneDigest,
                     String codeDigest, int attempts) {
        String value = "{\"codeDigest\":\"" + codeDigest + "\",\"status\":\""
                + VerificationCodeStatus.ACTIVE.name() + "\",\"attempts\":" + attempts + "}";
        redisTemplate.opsForValue().set(RedisKeys.code(purpose, phoneDigest), value,
                Duration.ofSeconds(RedisKeys.CODE_TTL_SECONDS));
    }

    /**
     * 原子校验并置 USED；错误 / 过期 / 重放统一抛 AUTHENTICATION_FAILED。
     *
     * @param purpose     用途（跨用途复用直接失败）
     * @param phoneDigest 手机号摘要
     * @param codeDigest  待校验摘要
     */
    public void verifyAndConsume(VerificationPurpose purpose, String phoneDigest,
                                 String codeDigest) {
        Long result = redisTemplate.execute(CONSUME_SCRIPT,
                Collections.singletonList(RedisKeys.code(purpose, phoneDigest)), codeDigest);
        if (result == null || result != 1L) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
    }

    /**
     * 清理指定用途的验证码（重置密码成功后调用）。
     *
     * @param purpose     用途
     * @param phoneDigest 手机号摘要
     */
    public void delete(VerificationPurpose purpose, String phoneDigest) {
        redisTemplate.delete(RedisKeys.code(purpose, phoneDigest));
    }

    /**
     * 校验键是否属于统一前缀（供 FoundationalIT 断言）。
     *
     * @param key Redis 键
     * @return 是否合规
     */
    public boolean hasValidPrefix(String key) {
        return key != null && key.startsWith(RedisKeys.PREFIX);
    }

    /** @return 全部 6 个键模板样例，供地基自检使用 */
    public List<String> templates() {
        return List.of(
                RedisKeys.code(VerificationPurpose.REGISTER, "<phoneDigest>"),
                RedisKeys.rateSend("<phoneDigest>"),
                RedisKeys.rateVerify("<phoneDigest>"),
                RedisKeys.rateLogin("<phoneDigest>"),
                RedisKeys.idempotency("<idempotencyKey>"),
                RedisKeys.sessionRevoked(0L));
    }
}
