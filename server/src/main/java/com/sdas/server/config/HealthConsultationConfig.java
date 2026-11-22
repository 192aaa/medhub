package com.sdas.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI健康咨询配置装配。
 *
 * <p>参照 {@code PharmacyConfig} 的显式注册方式，避免依赖包扫描顺序。
 * 配置唯一来源：specs/005-ai-health-consultation/data-model.md 第 6 节。</p>
 */
@Configuration
@EnableConfigurationProperties({
        HealthProperties.class,
        HealthEmergencyProperties.class,
        HospitalProperties.class
})
public class HealthConsultationConfig {
}
