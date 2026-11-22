package com.sdas.server.health.model;

import java.io.Serializable;

/**
 * 单个组件的健康状态。
 *
 * <p>字段定义见 specs/health-check/data-model.md 2.3。</p>
 */
public class ComponentStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String UNAVAILABLE = "unavailable";

    private String name;

    private HealthStatus status;

    private String detail;

    private long elapsedMs;

    /** 默认构造，供 Jackson 反序列化使用。 */
    public ComponentStatus() {
    }

    private ComponentStatus(String name, HealthStatus status, String detail, long elapsedMs) {
        this.name = name;
        this.status = status;
        this.detail = detail;
        this.elapsedMs = elapsedMs;
    }

    /**
     * 构造可用状态。
     *
     * @param name      组件名
     * @param detail    脱敏后的探针结果
     * @param elapsedMs 探针耗时（毫秒）
     * @return 组件状态
     */
    public static ComponentStatus up(String name, String detail, long elapsedMs) {
        return new ComponentStatus(name, HealthStatus.UP, detail, elapsedMs);
    }

    /**
     * 构造不可用状态，不回传异常细节。
     *
     * @param name      组件名
     * @param elapsedMs 探针耗时（毫秒）
     * @return 组件状态
     */
    public static ComponentStatus down(String name, long elapsedMs) {
        return new ComponentStatus(name, HealthStatus.DOWN, UNAVAILABLE, elapsedMs);
    }

    /** @return 组件名 */
    public String getName() {
        return name;
    }

    /** @param name 组件名 */
    public void setName(String name) {
        this.name = name;
    }

    /** @return 组件状态 */
    public HealthStatus getStatus() {
        return status;
    }

    /** @param status 组件状态 */
    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    /** @return 探针结果或 unavailable */
    public String getDetail() {
        return detail;
    }

    /** @param detail 探针结果或 unavailable */
    public void setDetail(String detail) {
        this.detail = detail;
    }

    /** @return 探针耗时（毫秒） */
    public long getElapsedMs() {
        return elapsedMs;
    }

    /** @param elapsedMs 探针耗时（毫秒） */
    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
