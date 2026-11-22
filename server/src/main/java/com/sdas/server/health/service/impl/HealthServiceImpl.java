package com.sdas.server.health.service.impl;

import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.Result;
import com.sdas.server.health.model.ComponentStatus;
import com.sdas.server.health.model.HealthReport;
import com.sdas.server.health.model.HealthStatus;
import com.sdas.server.health.repository.DatabaseProbeRepository;
import com.sdas.server.health.repository.RedisProbeRepository;
import com.sdas.server.health.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 健康检查业务编排：依次探测 server、database、redis 并聚合整体状态。
 *
 * <p>宪法原则 II：Service 负责编排，持久化访问全部经 Repository；
 * 原则 II 关键入参二次校验：对探针返回值做合法性校验，防止异常值被判为可用。</p>
 */
@Service
public class HealthServiceImpl implements HealthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthServiceImpl.class);

    private static final String SERVER = "server";

    private static final String DATABASE = "database";

    private static final String REDIS = "redis";

    private static final String PONG = "PONG";

    private final DatabaseProbeRepository databaseProbeRepository;

    private final RedisProbeRepository redisProbeRepository;

    /**
     * 构造健康检查服务。
     *
     * @param databaseProbeRepository 数据库探针
     * @param redisProbeRepository    Redis 探针
     */
    public HealthServiceImpl(DatabaseProbeRepository databaseProbeRepository,
                             RedisProbeRepository redisProbeRepository) {
        this.databaseProbeRepository = databaseProbeRepository;
        this.redisProbeRepository = redisProbeRepository;
    }

    /**
     * 执行健康检查，任一探针失败即整体 DOWN，且不中断其余探针。
     *
     * @return code=200 或 code=503 的统一响应，data 恒为健康报告
     */
    @Override
    public Result<HealthReport> check() {
        List<ComponentStatus> components = new ArrayList<>(3);
        components.add(ComponentStatus.up(SERVER, "running", 0L));
        components.add(probeDatabase());
        components.add(probeRedis());

        HealthStatus overall = components.stream()
                .allMatch(component -> component.getStatus() == HealthStatus.UP)
                ? HealthStatus.UP : HealthStatus.DOWN;
        HealthReport report = new HealthReport(overall, components, LocalDateTime.now());

        if (overall == HealthStatus.DOWN) {
            LOGGER.warn("Health check degraded, status={}", overall);
            return Result.failure(ErrorCode.HEALTH_UNAVAILABLE, report);
        }
        LOGGER.debug("Health check passed, all components UP");
        return Result.success(report);
    }

    private ComponentStatus probeDatabase() {
        long start = System.currentTimeMillis();
        try {
            Integer value = databaseProbeRepository.selectOne();
            if (value == null || value != 1) {
                throw new IllegalStateException();
            }
            return ComponentStatus.up(DATABASE, "SELECT 1 = " + value, elapsedMs(start));
        } catch (RuntimeException exception) {
            LOGGER.warn("Health probe [{}] failed, exception={}", DATABASE, exception.getClass().getSimpleName());
            return ComponentStatus.down(DATABASE, elapsedMs(start));
        }
    }

    private ComponentStatus probeRedis() {
        long start = System.currentTimeMillis();
        try {
            String result = redisProbeRepository.ping();
            if (!PONG.equalsIgnoreCase(result)) {
                throw new IllegalStateException();
            }
            return ComponentStatus.up(REDIS, "PING = " + result, elapsedMs(start));
        } catch (RuntimeException exception) {
            LOGGER.warn("Health probe [{}] failed, exception={}", REDIS, exception.getClass().getSimpleName());
            return ComponentStatus.down(REDIS, elapsedMs(start));
        }
    }

    private long elapsedMs(long start) {
        return System.currentTimeMillis() - start;
    }
}
