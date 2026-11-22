package com.sdas.server.identity.service.impl;

import com.sdas.server.identity.entity.SecurityAuditEvent;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.repository.SecurityAuditEventRepository;
import com.sdas.server.identity.service.AuditEventService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计事件写入实现。
 *
 * <p>注意 @Transactional(propagation = REQUIRES_NEW)：仅当由其他 Bean 经代理调用时生效，
 * 使审计写入与业务事务分离；无论事务如何，写入异常一律吞掉并记录 WARN，
 * 不得让审计失败打断注册 / 登录 / 认证主流程。
 * CONSULTATION_GATE_DENIED 的写入方为后续问诊模块，本功能内无调用点。</p>
 */
@Service
public class AuditEventServiceImpl implements AuditEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventServiceImpl.class);

    private final SecurityAuditEventRepository repository;

    /**
     * 构造审计服务。
     *
     * @param repository 审计事件仓储
     */
    public AuditEventServiceImpl(SecurityAuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType eventType, String result, Long actorPatientId,
                       String maskedPhone, String maskedIdCard, String reasonCode) {
        try {
            repository.insert(new SecurityAuditEvent(null, eventType, result, actorPatientId,
                    maskedPhone, maskedIdCard, reasonCode, MDC.get("traceId"),
                    LocalDateTime.now()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Audit event write failed, type={}", eventType);
        }
    }
}
