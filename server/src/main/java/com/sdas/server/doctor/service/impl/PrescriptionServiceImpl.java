package com.sdas.server.doctor.service.impl;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.doctor.dto.PrescriptionCreateRequest;
import com.sdas.server.doctor.dto.PrescriptionDetailResponse;
import com.sdas.server.doctor.dto.PrescriptionItemRequest;
import com.sdas.server.doctor.dto.PrescriptionItemResponse;
import com.sdas.server.doctor.dto.PrescriptionResponse;
import com.sdas.server.doctor.entity.DrugCatalog;
import com.sdas.server.doctor.entity.Prescription;
import com.sdas.server.doctor.entity.PrescriptionItem;
import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.doctor.repository.DrugCatalogRepository;
import com.sdas.server.doctor.repository.PrescriptionItemRepository;
import com.sdas.server.doctor.repository.PrescriptionRepository;
import com.sdas.server.doctor.service.PrescriptionService;
import com.sdas.server.doctor.support.PrescriptionCanonicalizer;
import com.sdas.server.doctor.support.PrescriptionSigningService;
import com.sdas.server.doctor.support.WatermarkService;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.pharmacy.support.PrescriptionQrTokenService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 电子处方实现：目录校验 → DRAFT 落库 → 服务端签名 → 二维码/水印 → SIGNED，全链路事务编排。
 * 一张问诊单仅一张处方（UNIQUE），签名失败不落库（回滚）。
 */
@Service
public class PrescriptionServiceImpl implements PrescriptionService {

    private static final int MAX_ITEMS = 10;

    private final ConsultationRepository consultationRepository;

    private final DrugCatalogRepository drugCatalogRepository;

    private final PrescriptionRepository prescriptionRepository;

    private final PrescriptionItemRepository prescriptionItemRepository;

    private final PrescriptionSigningService signingService;

    private final PrescriptionQrTokenService qrTokenService;

    private final WatermarkService watermarkService;

    private final DoctorProperties properties;

    private final AuditEventService auditEventService;

    /**
     * 构造处方服务。
     *
     * @param consultationRepository     问诊单仓储
     * @param drugCatalogRepository      药品目录仓储
     * @param prescriptionRepository     处方仓储
     * @param prescriptionItemRepository 处方明细仓储
     * @param signingService             签名服务
     * @param qrTokenService             二维码令牌服务（004）
     * @param watermarkService           水印服务
     * @param properties                 医生接诊配置
     * @param auditEventService          审计
     */
    public PrescriptionServiceImpl(ConsultationRepository consultationRepository,
                                   DrugCatalogRepository drugCatalogRepository,
                                   PrescriptionRepository prescriptionRepository,
                                   PrescriptionItemRepository prescriptionItemRepository,
                                   PrescriptionSigningService signingService,
                                   PrescriptionQrTokenService qrTokenService,
                                   WatermarkService watermarkService,
                                   DoctorProperties properties,
                                   AuditEventService auditEventService) {
        this.consultationRepository = consultationRepository;
        this.drugCatalogRepository = drugCatalogRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.signingService = signingService;
        this.qrTokenService = qrTokenService;
        this.watermarkService = watermarkService;
        this.properties = properties;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional
    public PrescriptionResponse create(long doctorId, long consultationId,
                                       PrescriptionCreateRequest request) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        if (!consultation.doctorId().equals(doctorId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_FORBIDDEN);
        }
        if (consultation.status() != ConsultationStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_CONSULTATION_STATUS);
        }
        if (prescriptionRepository.findByConsultationId(consultationId).isPresent()) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_ALREADY_EXISTS);
        }
        List<DrugCatalog> drugs = validateAndResolve(request.items());
        // 截断到秒并显式落库：签名原文含 createdAt，验真时需用库内值复验（见 PrescriptionCanonicalizer）
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime validUntil = now.plusDays(properties.prescriptionValidDays());

        long prescriptionId;
        try {
            prescriptionId = prescriptionRepository.insert(new Prescription(null, consultationId,
                    doctorId, consultation.patientId(), PrescriptionStatus.DRAFT, null, null, null,
                    null, null, now, null, 0));
        } catch (DuplicateKeyException exception) {
            // 上面的存在性预检查存在并发窗口，UNIQUE(consultation_id) 是最终保证
            throw new BusinessException(ErrorCode.PRESCRIPTION_ALREADY_EXISTS);
        }
        List<PrescriptionItem> items = resolveItems(prescriptionId, request.items(), drugs);
        for (PrescriptionItem item : items) {
            prescriptionItemRepository.insert(item);
        }

        String canonical = PrescriptionCanonicalizer.canonicalize(prescriptionId, doctorId,
                consultationId, items, now);
        String signature;
        try {
            signature = signingService.sign(canonical);
        } catch (BusinessException exception) {
            auditEventService.record(AuditEventType.PRESCRIPTION_SIGN_FAILED, "FAILURE",
                    consultation.patientId(), null, null, null);
            throw exception;
        }
        // 004：二维码内容为 v1 令牌 URL（处方号加密 + RSA 签名），取代 003 的明文处方号 + 截断签名
        String token = qrTokenService.issue(prescriptionId, validUntil);
        String qrCode = qrTokenService.linkOf(token);
        String watermark = watermarkService.buildWatermark(prescriptionId, now);
        prescriptionRepository.sign(prescriptionId, signature, qrCode, watermark, now, validUntil);

        auditEventService.record(AuditEventType.PRESCRIPTION_CREATED, "SUCCESS",
                consultation.patientId(), null, null, null);

        List<PrescriptionItemResponse> responses = buildItemResponses(items, drugs);
        return new PrescriptionResponse(prescriptionId, consultationId,
                PrescriptionStatus.SIGNED.name(), responses, now, validUntil, qrCode, watermark);
    }

    @Override
    public PrescriptionDetailResponse detail(long doctorId, long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        if (!prescription.doctorId().equals(doctorId)) {
            throw new BusinessException(ErrorCode.CONSULTATION_FORBIDDEN);
        }
        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(prescriptionId);
        List<PrescriptionItemResponse> responses = new ArrayList<>();
        for (PrescriptionItem item : items) {
            String drugName = drugCatalogRepository.findById(item.drugId())
                    .map(DrugCatalog::name).orElse(null);
            responses.add(new PrescriptionItemResponse(item.drugId(), drugName, item.spec(),
                    item.dosage(), item.caution()));
        }
        boolean expired = prescription.validUntil() != null
                && prescription.validUntil().isBefore(LocalDateTime.now());
        return new PrescriptionDetailResponse(prescriptionId, prescription.consultationId(),
                prescription.status().name(), responses, prescription.signedAt(),
                prescription.validUntil(), expired, prescription.qrCode(), prescription.watermark());
    }

    private List<DrugCatalog> validateAndResolve(List<PrescriptionItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new BusinessException(ErrorCode.INVALID_PRESCRIPTION_FORMAT);
        }
        List<DrugCatalog> drugs = new ArrayList<>();
        for (PrescriptionItemRequest item : items) {
            DrugCatalog drug = drugCatalogRepository.findByIdAndEnabled(item.drugId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DRUG_NOT_FOUND));
            drugs.add(drug);
        }
        return drugs;
    }

    private List<PrescriptionItem> resolveItems(long prescriptionId,
                                                List<PrescriptionItemRequest> requests,
                                                List<DrugCatalog> drugs) {
        List<PrescriptionItem> items = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            PrescriptionItemRequest request = requests.get(i);
            String spec = request.spec() != null ? request.spec() : drugs.get(i).spec();
            items.add(new PrescriptionItem(null, prescriptionId, request.drugId(), spec,
                    request.dosage(), request.caution(), null));
        }
        return items;
    }

    private List<PrescriptionItemResponse> buildItemResponses(List<PrescriptionItem> items,
                                                              List<DrugCatalog> drugs) {
        List<PrescriptionItemResponse> responses = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            PrescriptionItem item = items.get(i);
            responses.add(new PrescriptionItemResponse(item.drugId(), drugs.get(i).name(),
                    item.spec(), item.dosage(), item.caution()));
        }
        return responses;
    }

}
