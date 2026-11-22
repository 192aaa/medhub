package com.sdas.server.health.model;

/**
 * 健康检查状态枚举。
 *
 * <p>枚举取值唯一定义于 specs/health-check/data-model.md 1.1；
 * 宪法原则 II/III：前后端引用枚举时须标注该路径，禁止各自硬编码。</p>
 */
public enum HealthStatus {

    /** 组件可用。 */
    UP,
    /** 组件不可用。 */
    DOWN
}
