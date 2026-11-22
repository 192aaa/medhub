package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 患者注册与实名认证模块配置项（prefix = sdas.identity）。
 *
 * <p>16 项取值唯一来源：specs/001-patient-identity/data-model.md 第 6 节；
 * {@code cookie.secure} 不在本前缀下，由 {@link CookieProperties} 单独承载。</p>
 *
 * @param verificationCode 验证码策略
 * @param agreement        协议同意配置
 * @param jwt              令牌配置
 * @param crypto           加密密钥配置
 * @param whitelist        白名单来源配置
 */
@ConfigurationProperties(prefix = "sdas.identity")
public record IdentityProperties(
        VerificationCode verificationCode,
        Agreement agreement,
        Jwt jwt,
        Crypto crypto,
        Whitelist whitelist) {

    /** 验证码策略（D1：固定验证码仅 dev/test）。 */
    public record VerificationCode(
            boolean fixedEnabled,
            String fixedCode,
            long ttlSeconds,
            long sendIntervalSeconds,
            int dailyLimit,
            int maxAttempts) {
    }

    /** 协议同意配置。 */
    public record Agreement(String version) {
    }

    /** 令牌配置（access 7200 s，refresh 604800 s）。 */
    public record Jwt(long accessTokenTtlSeconds, long refreshTokenTtlSeconds, String secret) {
    }

    /** 加密密钥配置，禁止写死在代码中。 */
    public record Crypto(String idCardSecret, String phoneSecret, String aesKey) {
    }

    /** 白名单来源，仅 DATABASE_PRESET 合法（D2）。 */
    public record Whitelist(String source) {
    }
}
