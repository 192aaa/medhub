package com.sdas.server.health.service;

import com.sdas.server.common.Result;
import com.sdas.server.health.model.HealthReport;

/**
 * 健康检查业务编排接口。
 *
 * <p>契约见 specs/health-check/contracts/health-api.md。</p>
 */
public interface HealthService {

    /**
     * 聚合服务端、数据库与 Redis 三个探针的结果。
     *
     * @return code=200（全部 UP）或 code=503（任一 DOWN），data 恒为健康报告
     */
    Result<HealthReport> check();
}
