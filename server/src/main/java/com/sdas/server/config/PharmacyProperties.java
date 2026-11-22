package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 处方取药模块配置项（prefix = sdas.pharmacy）。
 *
 * <p>取值唯一来源：specs/004-prescription-dispensing/data-model.md §6。</p>
 *
 * @param qr 二维码令牌与图片配置
 */
@ConfigurationProperties(prefix = "sdas.pharmacy")
public record PharmacyProperties(Qr qr) {

    /**
     * 二维码配置。
     *
     * @param verifyUrlTemplate 验真链接模板（单个 {@code %s} 占位令牌）
     * @param tokenVersion      令牌版本前缀（固定 {@code v1}）
     * @param verifyCodeLength  手工输入验真码长度（默认 12）
     * @param imageSize         二维码 PNG 边长（默认 480）
     * @param errorCorrection   纠错级别（默认 {@code M}）
     * @param tokenGraceDays    令牌宽限天数（令牌 exp = 处方 valid_until + 本值）
     */
    public record Qr(
            String verifyUrlTemplate,
            String tokenVersion,
            int verifyCodeLength,
            int imageSize,
            String errorCorrection,
            int tokenGraceDays) {
    }
}
