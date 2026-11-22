package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 医院结构化信息（prefix = sdas.hospital）。
 *
 * <p>取值来源：specs/005-ai-health-consultation/data-model.md 第 6 节（R-05）。
 * 医院信息走配置而不塞进知识库，避免结构化字段被当作检索条目污染 Top-K。</p>
 *
 * @param name           医院名称
 * @param address        地址
 * @param phone          咨询电话
 * @param emergencyPhone 急救电话
 * @param serviceHours   门诊时间
 */
@ConfigurationProperties(prefix = "sdas.hospital")
public record HospitalProperties(
        String name,
        String address,
        String phone,
        String emergencyPhone,
        String serviceHours) {
}
