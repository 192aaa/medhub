package com.sdas.server.identity.support;

import com.sdas.server.identity.model.VerificationPurpose;

/**
 * Redis 键模板，唯一来源：specs/001-patient-identity/data-model.md 第 4 节（6 个模板）。
 */
public final class RedisKeys {

    /** 统一前缀。 */
    public static final String PREFIX = "sdas:identity:";
    /** 验证码生命周期 TTL（秒）。 */
    public static final long CODE_TTL_SECONDS = 300;
    /** 幂等键 TTL（秒）。 */
    public static final long IDEMPOTENCY_TTL_SECONDS = 600;

    private RedisKeys() {
    }

    /** @param purpose 用途 @param phoneDigest 手机号摘要 @return 验证码键 */
    public static String code(VerificationPurpose purpose, String phoneDigest) {
        return PREFIX + "code:" + purpose.name() + ":" + phoneDigest;
    }

    /** @param phoneDigest 手机号摘要 @return 发送频次键 */
    public static String rateSend(String phoneDigest) {
        return PREFIX + "rate:send:" + phoneDigest;
    }

    /** @param phoneDigest 手机号摘要 @return 实名认证尝试频次键 */
    public static String rateVerify(String phoneDigest) {
        return PREFIX + "rate:verify:" + phoneDigest;
    }

    /** @param phoneDigest 手机号摘要 @return 登录失败频次键 */
    public static String rateLogin(String phoneDigest) {
        return PREFIX + "rate:login:" + phoneDigest;
    }

    /** @param idempotencyKey 幂等键 @return 幂等快照键 */
    public static String idempotency(String idempotencyKey) {
        return PREFIX + "idem:" + idempotencyKey;
    }

    /** @param sessionId 会话主键 @return 会话撤销标记键 */
    public static String sessionRevoked(long sessionId) {
        return PREFIX + "session:revoked:" + sessionId;
    }
}
