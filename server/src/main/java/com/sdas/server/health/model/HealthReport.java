package com.sdas.server.health.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康检查报告。
 *
 * <p>字段定义见 specs/health-check/data-model.md 2.2；checkedAt 由 JacksonConfig 全局序列化为 ISO 8601。</p>
 */
public class HealthReport implements Serializable {

    private static final long serialVersionUID = 1L;

    private HealthStatus status;

    private List<ComponentStatus> components;

    private LocalDateTime checkedAt;

    /** 默认构造，供 Jackson 反序列化使用。 */
    public HealthReport() {
    }

    /**
     * 构造健康报告。
     *
     * @param status     整体状态
     * @param components 组件状态列表，顺序固定 server → database → redis
     * @param checkedAt  检查时间
     */
    public HealthReport(HealthStatus status, List<ComponentStatus> components, LocalDateTime checkedAt) {
        this.status = status;
        this.components = components;
        this.checkedAt = checkedAt;
    }

    /** @return 整体状态 */
    public HealthStatus getStatus() {
        return status;
    }

    /** @param status 整体状态 */
    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    /** @return 组件状态列表 */
    public List<ComponentStatus> getComponents() {
        return components;
    }

    /** @param components 组件状态列表 */
    public void setComponents(List<ComponentStatus> components) {
        this.components = components;
    }

    /** @return 检查时间 */
    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    /** @param checkedAt 检查时间 */
    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}
