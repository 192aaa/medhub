package com.sdas.server.doctor.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.doctor.dto.PrescriptionCreateRequest;
import com.sdas.server.doctor.dto.PrescriptionItemRequest;
import com.sdas.server.doctor.dto.PrescriptionResponse;
import com.sdas.server.doctor.entity.DrugCatalog;
import com.sdas.server.doctor.entity.Prescription;
import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.doctor.repository.DrugCatalogRepository;
import com.sdas.server.doctor.repository.PrescriptionItemRepository;
import com.sdas.server.doctor.repository.PrescriptionRepository;
import com.sdas.server.doctor.service.impl.PrescriptionServiceImpl;
import com.sdas.server.doctor.support.PrescriptionSigningService;
import com.sdas.server.pharmacy.support.PrescriptionQrTokenService;
import com.sdas.server.doctor.support.WatermarkService;
import com.sdas.server.identity.service.AuditEventService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

/** PrescriptionService 单测（T056）：开方成功、药品不存在、重复开方、签名失败。 */
class PrescriptionServiceTest {

    private ConsultationRepository consultationRepository;
    private DrugCatalogRepository drugCatalogRepository;
    private PrescriptionRepository prescriptionRepository;
    private PrescriptionSigningService signingService;
    private PrescriptionQrTokenService qrTokenService;
    private PrescriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationRepository.class);
        drugCatalogRepository = mock(DrugCatalogRepository.class);
        prescriptionRepository = mock(PrescriptionRepository.class);
        PrescriptionItemRepository itemRepository = mock(PrescriptionItemRepository.class);
        signingService = mock(PrescriptionSigningService.class);
        qrTokenService = mock(PrescriptionQrTokenService.class);
        WatermarkService watermarkService = mock(WatermarkService.class);
        DoctorProperties properties = new DoctorProperties(24, 1000, 3,
                new DoctorProperties.Signature("p", "a", "pw", "SHA256withRSA"),
                new DoctorProperties.Prescription(
                        "http://localhost/verify?prescriptionId=%s&sig=%s", "示例医院"));
        AuditEventService audit = mock(AuditEventService.class);
        service = new PrescriptionServiceImpl(consultationRepository, drugCatalogRepository,
                prescriptionRepository, itemRepository, signingService, qrTokenService,
                watermarkService, properties, audit);
        when(qrTokenService.issue(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any())).thenReturn("v1.cipher.sig");
        when(qrTokenService.linkOf("v1.cipher.sig"))
                .thenReturn("http://localhost:5183/pharmacy/verify?t=v1.cipher.sig");
    }

    @Test
    void createSucceeds() {
        Consultation consultation = consultation(701L, 101L, ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(consultation));
        when(prescriptionRepository.findByConsultationId(701L)).thenReturn(Optional.empty());
        when(drugCatalogRepository.findByIdAndEnabled(901L)).thenReturn(Optional.of(drug()));
        when(prescriptionRepository.insert(org.mockito.ArgumentMatchers.any(Prescription.class)))
                .thenReturn(1001L);
        when(signingService.sign(anyString())).thenReturn("signature-value");

        PrescriptionResponse response = service.create(101L, 701L, request());
        assertEquals(1001L, response.prescriptionId());
        assertEquals("SIGNED", response.status());
        assertEquals("阿莫西林胶囊", response.items().get(0).drugName());
        assertEquals("http://localhost:5183/pharmacy/verify?t=v1.cipher.sig", response.qrCode());
    }

    @Test
    void createSignsResolvedSpecWhenRequestSpecAbsent() {
        Consultation consultation = consultation(701L, 101L, ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(consultation));
        when(prescriptionRepository.findByConsultationId(701L)).thenReturn(Optional.empty());
        when(drugCatalogRepository.findByIdAndEnabled(901L)).thenReturn(Optional.of(drug()));
        when(prescriptionRepository.insert(org.mockito.ArgumentMatchers.any(Prescription.class)))
                .thenReturn(1001L);
        when(signingService.sign(anyString())).thenReturn("signature-value");

        service.create(101L, 701L, request());

        ArgumentCaptor<String> canonical = ArgumentCaptor.forClass(String.class);
        verify(signingService).sign(canonical.capture());
        assertTrue(canonical.getValue()
                .contains("901:0.25g*20:每日三次，每次一片:饭后服用"));
    }

    @Test
    void createDrugNotFoundThrows() {
        Consultation consultation = consultation(701L, 101L, ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(consultation));
        when(prescriptionRepository.findByConsultationId(701L)).thenReturn(Optional.empty());
        when(drugCatalogRepository.findByIdAndEnabled(901L)).thenReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.create(101L, 701L, request()));
        assertEquals(ErrorCode.DRUG_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void createDuplicateThrows() {
        Consultation consultation = consultation(701L, 101L, ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(consultation));
        when(prescriptionRepository.findByConsultationId(701L))
                .thenReturn(Optional.of(new Prescription(1001L, 701L, 101L, 10001L,
                        com.sdas.server.doctor.model.PrescriptionStatus.SIGNED, "sig", null, null,
                        null, null, null, null, 0)));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.create(101L, 701L, request()));
        assertEquals(ErrorCode.PRESCRIPTION_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void createConcurrentInsertConflictMapsToAlreadyExists() {
        Consultation consultation = consultation(701L, 101L, ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findById(701L)).thenReturn(Optional.of(consultation));
        when(prescriptionRepository.findByConsultationId(701L)).thenReturn(Optional.empty());
        when(drugCatalogRepository.findByIdAndEnabled(901L))
                .thenReturn(Optional.of(drug()));
        when(prescriptionRepository.insert(org.mockito.ArgumentMatchers.any(Prescription.class)))
                .thenThrow(new DuplicateKeyException("uk_prescription_consultation"));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.create(101L, 701L, request()));
        assertEquals(ErrorCode.PRESCRIPTION_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void detailNotFoundThrowsPrescriptionNotFound() {
        when(prescriptionRepository.findById(1001L)).thenReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.detail(101L, 1001L));
        assertEquals(ErrorCode.PRESCRIPTION_NOT_FOUND, exception.getErrorCode());
    }

    private DrugCatalog drug() {
        return new DrugCatalog(901L, "阿莫西林胶囊", "0.25g*20", "示例制药", "胶囊",
                "盒", true, true, "PRESET", null, null, 0);
    }

    private PrescriptionCreateRequest request() {
        return new PrescriptionCreateRequest(List.of(
                new PrescriptionItemRequest(901L, null, "每日三次，每次一片", "饭后服用")));
    }

    private Consultation consultation(long id, long doctorId, ConsultationStatus status) {
        return new Consultation(id, 1L, 10001L, doctorId, 1L, "咳嗽三天", null, null, status,
                null, null, ConsultationTimeoutStatus.NONE, null, null, null, null, 0);
    }
}
