package com.sdas.server.pharmacy.service.impl;

import com.sdas.server.appointment.entity.Doctor;
import com.sdas.server.appointment.repository.DoctorRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.doctor.entity.Prescription;
import com.sdas.server.doctor.entity.PrescriptionItem;
import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.doctor.repository.DrugCatalogRepository;
import com.sdas.server.doctor.repository.PrescriptionItemRepository;
import com.sdas.server.doctor.repository.PrescriptionRepository;
import com.sdas.server.doctor.support.PrescriptionCanonicalizer;
import com.sdas.server.doctor.support.PrescriptionSigningService;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.pharmacy.dto.PrescriptionItemView;
import com.sdas.server.pharmacy.dto.VerificationRequest;
import com.sdas.server.pharmacy.dto.VerificationResponse;
import com.sdas.server.pharmacy.entity.DispenseRecord;
import com.sdas.server.pharmacy.entity.PharmacyAccount;
import com.sdas.server.pharmacy.model.DispenseStatus;
import com.sdas.server.pharmacy.repository.DispenseRecordRepository;
import com.sdas.server.pharmacy.repository.PharmacyAccountRepository;
import com.sdas.server.pharmacy.service.DispenseStatusResolver;
import com.sdas.server.pharmacy.service.PrescriptionVerificationService;
import com.sdas.server.pharmacy.support.PrescriptionQrTokenService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 处方验真实现：凭证校验（令牌 + 验真码）→ 处方存在性 → 内容签名复验 → 派生状态组装。
 *
 * <p>失败时响应体不含任何处方字段（FR-007），审计仅记凭证 SHA-256 前 16 位。</p>
 */
@Service
public class PrescriptionVerificationServiceImpl implements PrescriptionVerificationService {

    private final PrescriptionQrTokenService tokenService;

    private final PrescriptionRepository prescriptionRepository;

    private final PrescriptionItemRepository itemRepository;

    private final DrugCatalogRepository drugCatalogRepository;

    private final DispenseRecordRepository dispenseRecordRepository;

    private final PharmacyAccountRepository pharmacyAccountRepository;

    private final DoctorRepository doctorRepository;

    private final PrescriptionSigningService signingService;

    private final AuditEventService auditEventService;

    /**
     * 构造验真服务。
     *
     * @param tokenService               二维码令牌服务
     * @param prescriptionRepository     处方仓储
     * @param itemRepository             处方明细仓储
     * @param drugCatalogRepository      药品目录仓储
     * @param dispenseRecordRepository   核销记录仓储
     * @param pharmacyAccountRepository  药店账号仓储
     * @param doctorRepository           医生仓储
     * @param signingService             签名服务（复验处方内容签名）
     * @param auditEventService          审计服务
     */
    public PrescriptionVerificationServiceImpl(PrescriptionQrTokenService tokenService,
                                               PrescriptionRepository prescriptionRepository,
                                               PrescriptionItemRepository itemRepository,
                                               DrugCatalogRepository drugCatalogRepository,
                                               DispenseRecordRepository dispenseRecordRepository,
                                               PharmacyAccountRepository pharmacyAccountRepository,
                                               DoctorRepository doctorRepository,
                                               PrescriptionSigningService signingService,
                                               AuditEventService auditEventService) {
        this.tokenService = tokenService;
        this.prescriptionRepository = prescriptionRepository;
        this.itemRepository = itemRepository;
        this.drugCatalogRepository = drugCatalogRepository;
        this.dispenseRecordRepository = dispenseRecordRepository;
        this.pharmacyAccountRepository = pharmacyAccountRepository;
        this.doctorRepository = doctorRepository;
        this.signingService = signingService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationResponse verify(VerificationRequest request) {
        String token = null;
        try {
            Proof proof = resolveProof(request);
            token = proof.token();
            Prescription prescription = prescriptionRepository.findById(proof.prescriptionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND));
            if (prescription.status() == PrescriptionStatus.DRAFT) {
                throw new BusinessException(ErrorCode.PRESCRIPTION_NOT_SIGNED);
            }
            List<PrescriptionItem> items = itemRepository.findByPrescriptionId(prescription.id());
            if (!signingService.verify(PrescriptionCanonicalizer.canonicalize(prescription, items),
                    prescription.signature())) {
                throw new BusinessException(ErrorCode.INVALID_VERIFICATION_PROOF);
            }
            VerificationResponse response = buildResponse(prescription, items, token);
            auditEventService.record(AuditEventType.PRESCRIPTION_VERIFICATION_SUCCESS, "SUCCESS",
                    null, null, null, null);
            return response;
        } catch (BusinessException exception) {
            auditEventService.record(AuditEventType.PRESCRIPTION_VERIFICATION_FAILED, "FAILURE",
                    null, null, null, token == null ? null : tokenService.auditDigest(token));
            throw exception;
        }
    }

    private Proof resolveProof(VerificationRequest request) {
        boolean hasQr = StringUtils.hasText(request.qrContent());
        boolean hasManual = request.prescriptionId() != null;
        if (hasQr == hasManual) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (hasQr) {
            String token = tokenService.extractToken(request.qrContent());
            return new Proof(tokenService.verify(token), token);
        }
        if (!StringUtils.hasText(request.verifyCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Prescription prescription = prescriptionRepository.findById(request.prescriptionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        String stored = prescription.qrCode();
        if (stored == null || !tokenService.matchesVerifyCode(stored, request.verifyCode())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_PROOF);
        }
        return new Proof(prescription.id(), tokenService.extractToken(stored));
    }

    private VerificationResponse buildResponse(Prescription prescription,
                                               List<PrescriptionItem> items, String token) {
        LocalDateTime now = LocalDateTime.now();
        Optional<DispenseRecord> record =
                dispenseRecordRepository.findByPrescriptionId(prescription.id());
        DispenseStatus dispenseStatus = DispenseStatusResolver.resolve(prescription.status(),
                record.isPresent(), prescription.validUntil(), now);
        String dispensedByMask = record
                .flatMap(item -> pharmacyAccountRepository.findById(item.pharmacyId()))
                .map(PharmacyAccount::nameMask)
                .orElse(null);
        return new VerificationResponse("VALID", prescription.id(), doctorNameMask(prescription),
                prescription.signedAt(), prescription.validUntil(),
                DispenseStatusResolver.isExpired(prescription.validUntil(), now),
                prescription.status().name(), dispenseStatus.name(),
                DispenseStatusResolver.dispensable(dispenseStatus),
                record.map(DispenseRecord::dispensedAt).orElse(null), dispensedByMask,
                record.map(DispenseRecord::id).orElse(null), prescription.watermark(),
                tokenService.verifyCode(token), itemViews(items));
    }

    private String doctorNameMask(Prescription prescription) {
        return doctorRepository.findById(prescription.doctorId())
                .map(Doctor::name)
                .orElse(null);
    }

    private List<PrescriptionItemView> itemViews(List<PrescriptionItem> items) {
        List<PrescriptionItemView> views = new ArrayList<>();
        for (PrescriptionItem item : items) {
            String drugName = drugCatalogRepository.findById(item.drugId())
                    .map(drug -> drug.name())
                    .orElse(null);
            views.add(new PrescriptionItemView(item.drugId(), drugName, item.spec(),
                    item.dosage(), item.caution()));
        }
        return views;
    }

    /**
     * 凭证解析结果。
     *
     * @param prescriptionId 处方主键
     * @param token          裸令牌
     */
    private record Proof(long prescriptionId, String token) {
    }
}
