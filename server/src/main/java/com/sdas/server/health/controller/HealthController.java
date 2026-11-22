package com.sdas.server.health.controller;

import com.sdas.server.common.Result;
import com.sdas.server.health.model.HealthReport;
import com.sdas.server.health.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 *
 * <p>宪法原则 II：Controller 仅调用 Service 并返回响应，不写业务逻辑；
 * 契约见 specs/health-check/contracts/health-api.md。</p>
 */
@RestController
public class HealthController {

    private final HealthService healthService;

    /**
     * 构造健康检查控制器。
     *
     * @param healthService 健康检查服务
     */
    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * 查询服务端、数据库与 Redis 健康状态。
     *
     * @return HTTP 200（全部 UP）或 503（任一 DOWN），code 与 HTTP 状态一致
     */
    @GetMapping("/api/health")
    public ResponseEntity<Result<HealthReport>> health() {
        Result<HealthReport> result = healthService.check();
        return ResponseEntity.status(result.getCode()).body(result);
    }
}
