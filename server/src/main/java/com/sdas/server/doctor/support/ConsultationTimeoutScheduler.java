package com.sdas.server.doctor.support;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接诊超时兜底调度器（{@code @Scheduled} 定时扫描 + 状态迁移，不引入消息队列）。
 *
 * <p>扫描接诊超过 {@code timeout-hours} 且未回复/开药的 {@code IN_PROGRESS} 问诊单，
 * 置 {@code timeout_status=ADMIN_NOTIFIED} 并写审计；{@code timeout_status=NONE} 作幂等条件避免重复通知。</p>
 */
@Component
public class ConsultationTimeoutScheduler {

    private final ConsultationRepository consultationRepository;

    private final DoctorProperties properties;

    private final AuditEventService auditEventService;

    /**
     * 构造超时调度器。
     *
     * @param consultationRepository 问诊单仓储
     * @param properties             医生接诊配置
     * @param auditEventService      审计
     */
    public ConsultationTimeoutScheduler(ConsultationRepository consultationRepository,
                                        DoctorProperties properties,
                                        AuditEventService auditEventService) {
        this.consultationRepository = consultationRepository;
        this.properties = properties;
        this.auditEventService = auditEventService;
    }

    /**
     * 周期扫描并标记超时问诊单（每分钟一次）。
     */
    @Scheduled(fixedDelayString = "60000")
    @Transactional
    public void scanAndMarkTimeout() {
        LocalDateTime acceptedBefore = LocalDateTime.now().minusHours(properties.timeoutHours());
        List<Consultation> candidates = consultationRepository.findTimeoutCandidates(acceptedBefore);
        for (Consultation consultation : candidates) {
            int updated = consultationRepository.markTimeout(consultation.id());
            if (updated > 0) {
                auditEventService.record(AuditEventType.CONSULTATION_TIMEOUT, "SUCCESS",
                        consultation.patientId(), null, null, null);
            }
        }
    }
}
