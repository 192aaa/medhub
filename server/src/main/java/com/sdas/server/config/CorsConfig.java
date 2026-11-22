package com.sdas.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 服务端跨域配置。
 *
 * <p>宪法原则 III：服务端支持跨域；白名单由配置注入，禁止与 allowCredentials=true 同时使用通配符。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${sdas.cors.allowed-origin-patterns}")
    private String[] allowedOriginPatterns;

    /**
     * 注册 CORS 映射。
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
