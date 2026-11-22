package com.sdas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 预约挂号与图文问诊模块配置（prefix = sdas.appointment）。
 *
 * <p>取值来源：specs/002-appointment-consultation/data-model.md 第 3 节。
 * mockPaymentEnabled 仅 dev/test 为 true，prod 须显式关闭（analysis.md RK-04）。</p>
 *
 * @param mockPaymentEnabled 是否启用模拟支付（MVP 默认 true）
 * @param paymentTimeoutSeconds 待支付超时（秒），超时后挂号单置 CLOSED
 */
@ConfigurationProperties(prefix = "sdas.appointment")
public record AppointmentProperties(boolean mockPaymentEnabled, long paymentTimeoutSeconds) {
}
