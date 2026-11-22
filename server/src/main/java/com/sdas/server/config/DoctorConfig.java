package com.sdas.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 注册医生接诊模块的配置属性绑定（构造器绑定，支持 record），并启用接诊超时定时调度。
 */
@Configuration
@EnableConfigurationProperties({DoctorProperties.class, DecisionProperties.class,
        TreatmentDecisionProperties.class})
@EnableScheduling
public class DoctorConfig {
}
