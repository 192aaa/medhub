package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 医生接诊模块配置项（prefix = sdas.doctor）。
 *
 * <p>取值唯一来源：specs/003-doctor-consultation/data-model.md 第 3 节。</p>
 *
 * @param timeoutHours          接诊超时（小时）
 * @param replyMaxChars         医生回复单条上限（字符）
 * @param prescriptionValidDays 处方默认有效期（天）
 * @param signature             处方签名密钥配置
 * @param prescription          处方二维码/水印配置
 */
@ConfigurationProperties(prefix = "sdas.doctor")
public record DoctorProperties(
        int timeoutHours,
        int replyMaxChars,
        int prescriptionValidDays,
        Signature signature,
        Prescription prescription) {

    /** 处方签名密钥配置（服务端托管，禁止明文落库）。 */
    public record Signature(
            String keyStorePath,
            String keyAlias,
            String keyStorePassword,
            String algorithm) {
    }

    /** 处方二维码验真链接与水印配置。 */
    public record Prescription(
            String verifyUrlTemplate,
            String watermarkPrefix) {
    }
}
