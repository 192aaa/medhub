package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cookie 安全配置（prefix = cookie）。
 *
 * <p>{@code cookie.secure} 不属于 sdas.identity 前缀，故不能并入
 * {@link IdentityProperties}；生产必须为 true。</p>
 *
 * @param secure 是否为 Cookie 添加 Secure 属性
 */
@ConfigurationProperties(prefix = "cookie")
public record CookieProperties(boolean secure) {
}
