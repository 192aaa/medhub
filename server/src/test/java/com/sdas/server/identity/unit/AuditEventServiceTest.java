package com.sdas.server.identity.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sdas.server.identity.entity.SecurityAuditEvent;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.repository.SecurityAuditEventRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.impl.AuditEventServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * T029 前置单测：10 类事件字段完整性、写入异常不影响主流程。
 */
class AuditEventServiceTest {

    private SecurityAuditEventRepository repository;

    private AuditEventService service;

    @BeforeEach
    void setUp() {
        repository = mock(SecurityAuditEventRepository.class);
        service = new AuditEventServiceImpl(repository);
    }

    @Test
    void allEventTypesAreRecordable() {
        for (AuditEventType type : AuditEventType.values()) {
            service.record(type, "SUCCESS", 10001L, "138****8000", null, null);
        }
        verify(repository, org.mockito.Mockito.times(AuditEventType.values().length))
                .insert(any(SecurityAuditEvent.class));
    }

    @Test
    void recordKeepsOnlyMaskedFields() {
        service.record(AuditEventType.PATIENT_REGISTERED, "SUCCESS", 10001L,
                "138****8000", "110***********002X", "INTERNAL_REASON");
        ArgumentCaptor<SecurityAuditEvent> captor =
                ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).insert(captor.capture());
        SecurityAuditEvent event = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(AuditEventType.PATIENT_REGISTERED,
                event.eventType());
        org.junit.jupiter.api.Assertions.assertEquals("138****8000", event.maskedPhone());
        org.junit.jupiter.api.Assertions.assertEquals("110***********002X", event.maskedIdCard());
        org.junit.jupiter.api.Assertions.assertNull(event.id());
    }

    @Test
    void writeFailureDoesNotBreakMainFlow() {
        doThrow(new RuntimeException("db down")).when(repository)
                .insert(any(SecurityAuditEvent.class));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.record(AuditEventType.PATIENT_LOGIN_FAILED, "FAILURE",
                        null, null, null, null));
    }
}
