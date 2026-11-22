package com.sdas.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册处方取药模块的配置属性绑定（构造器绑定，支持 record）。
 */
@Configuration
@EnableConfigurationProperties({PharmacyProperties.class})
public class PharmacyConfig {
}
