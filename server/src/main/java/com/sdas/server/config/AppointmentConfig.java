package com.sdas.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 预约挂号与图文问诊模块配置装配。
 *
 * <p>绑定三个 {@code @ConfigurationProperties} 并注册问诊图片的只读静态资源映射
 * （servePath → location 本地目录）。</p>
 */
@Configuration
@EnableConfigurationProperties({
        AppointmentProperties.class,
        TriageProperties.class,
        ImageStorageProperties.class
})
public class AppointmentConfig implements WebMvcConfigurer {

    private final ImageStorageProperties imageStorageProperties;

    /**
     * 构造配置。
     *
     * @param imageStorageProperties 图片存储配置
     */
    public AppointmentConfig(ImageStorageProperties imageStorageProperties) {
        this.imageStorageProperties = imageStorageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(imageStorageProperties.servePath() + "/**")
                .addResourceLocations("file:" + imageStorageProperties.location() + "/");
    }
}
