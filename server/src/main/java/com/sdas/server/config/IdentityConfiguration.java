package com.sdas.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册患者身份模块的配置属性绑定（构造器绑定，支持 record）。
 */
@Configuration
@EnableConfigurationProperties({IdentityProperties.class, CookieProperties.class})
public class IdentityConfiguration {
}
