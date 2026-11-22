package com.sdas.server.identity.service;

/**
 * 频次限制：验证码发送 60 s 间隔 + 单日 10 次、登录失败 15 分钟窗口、实名认证 15 分钟窗口。
 */
public interface RateLimitService {

    /**
     * 校验验证码发送频次，超限抛 RATE_LIMITED（不返回剩余次数）。
     *
     * @param phoneDigest 手机号摘要
     */
    void checkSend(String phoneDigest);

    /**
     * 累计实名认证尝试，15 分钟窗口超限抛 RATE_LIMITED。
     *
     * @param phoneDigest 手机号摘要
     */
    void checkIdentityVerification(String phoneDigest);

    /**
     * 累计登录失败，达阈值后返回 true（仍返回统一 AUTHENTICATION_FAILED，不锁号）。
     *
     * @param phoneDigest 手机号摘要
     * @return 是否跳过密码校验
     */
    boolean shouldSkipPasswordCheck(String phoneDigest);

    /** @return 发送间隔（秒），供契约响应回显 */
    long sendIntervalSeconds();
}
