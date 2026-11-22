package com.sdas.server.appointment.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.model.AuthStatus;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.appointment.repository.PatientHistoryVisitRepository;
import com.sdas.server.identity.entity.PatientAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

/** 复诊资格门禁单测（tasks.md T022）。 */
@ExtendWith(MockitoExtension.class)
class RevisitEligibilityServiceTest {

    @Mock
    private PatientAccountRepository accountRepository;

    @Mock
    private PatientHistoryVisitRepository historyRepository;

    @Mock
    private AuditEventService auditEventService;

    @InjectMocks
    private RevisitEligibilityService service;

    @Test
    void eligibleWhenVerifiedAndHasHistory() {
        PatientAccount account = mock(PatientAccount.class);
        when(account.authStatus()).thenReturn(AuthStatus.VERIFIED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(historyRepository.existsByPatientId(1L)).thenReturn(true);

        assertDoesNotThrow(() -> service.assertEligible(1L));
    }

    @Test
    void rejectsWhenNotVerifiedAndAudits() {
        PatientAccount account = mock(PatientAccount.class);
        when(account.authStatus()).thenReturn(AuthStatus.UNVERIFIED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(historyRepository.existsByPatientId(1L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assertEligible(1L));
        assertEquals(ErrorCode.REVISIT_NOT_ELIGIBLE, ex.getErrorCode());
        verify(auditEventService).record(eq(AuditEventType.REVISIT_GATE_DENIED), any(), eq(1L), any(), any(), any());
    }

    @Test
    void rejectsWhenNoHistory() {
        PatientAccount account = mock(PatientAccount.class);
        when(account.authStatus()).thenReturn(AuthStatus.VERIFIED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(historyRepository.existsByPatientId(1L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assertEligible(1L));
        assertEquals(ErrorCode.REVISIT_NOT_ELIGIBLE, ex.getErrorCode());
    }
}
