package com.sdas.server.appointment.support;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.model.AuthStatus;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.appointment.repository.PatientHistoryVisitRepository;
import org.springframework.stereotype.Service;

/**
 * 复诊资格判定（FR-007 门禁）：已实名认证（VERIFIED）且存在历史就诊记录。
 *
 * <p>不满足时抛出 {@link ErrorCode#REVISIT_NOT_ELIGIBLE} 并写 {@code REVISIT_GATE_DENIED} 审计，
 * 不泄漏账号存在性或资格判定细节（SC-003）。</p>
 */
@Service
public class RevisitEligibilityService {

    private final PatientAccountRepository accountRepository;

    private final PatientHistoryVisitRepository historyRepository;

    private final AuditEventService auditEventService;

    /**
     * 构造复诊资格服务。
     *
     * @param accountRepository 账户仓储（复用 001）
     * @param historyRepository 历史就诊仓储
     * @param auditEventService 审计服务
     */
    public RevisitEligibilityService(PatientAccountRepository accountRepository,
                                    PatientHistoryVisitRepository historyRepository,
                                    AuditEventService auditEventService) {
        this.accountRepository = accountRepository;
        this.historyRepository = historyRepository;
        this.auditEventService = auditEventService;
    }

    /**
     * 断言患者具备复诊资格。
     *
     * @param patientId 患者主键
     */
    public void assertEligible(long patientId) {
        boolean verified = accountRepository.findById(patientId)
                .map(account -> account.authStatus() == AuthStatus.VERIFIED)
                .orElse(false);
        boolean hasHistory = historyRepository.existsByPatientId(patientId);
        if (!verified || !hasHistory) {
            auditEventService.record(AuditEventType.REVISIT_GATE_DENIED, "REJECTED", patientId,
                    null, null, ErrorCode.REVISIT_NOT_ELIGIBLE.name());
            throw new BusinessException(ErrorCode.REVISIT_NOT_ELIGIBLE);
        }
    }
}
