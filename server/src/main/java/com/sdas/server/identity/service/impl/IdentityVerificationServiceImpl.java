package com.sdas.server.identity.service.impl;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.identity.dto.IdentityStatusResponse;
import com.sdas.server.identity.dto.IdentityVerificationRequest;
import com.sdas.server.identity.dto.IdentityVerificationResponse;
import com.sdas.server.identity.entity.PatientAccount;
import com.sdas.server.identity.entity.PatientIdentity;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.model.AuthStatus;
import com.sdas.server.identity.repository.HospitalIdentityWhitelistRepository;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.repository.PatientIdentityRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.IdentityVerificationService;
import com.sdas.server.identity.service.MaskService;
import com.sdas.server.identity.service.RateLimitService;
import com.sdas.server.identity.service.TokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实名认证事务：格式校验 → 白名单精确查询 → patient_identity 插入 +
 * patient_account.auth_status 更新 + 审计，全部在同一 {@code @Transactional}。
 *
 * <p>未命中 / 证件已被他人绑定只写 IDENTITY_REJECTED 审计并返回统一文案，
 * 不暴露白名单命中信息、不返回 IdentityVerificationResult 枚举（FR-019、SC-005）。</p>
 */
@Service
public class IdentityVerificationServiceImpl implements IdentityVerificationService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private static final String STATUS_VERIFIED = "VERIFIED";

    private final PatientIdExtractor patientIdExtractor;

    private final PatientAccountRepository accountRepository;

    private final PatientIdentityRepository identityRepository;

    private final HospitalIdentityWhitelistRepository whitelistRepository;

    private final CryptoService cryptoService;

    private final MaskService maskService;

    private final RateLimitService rateLimitService;

    private final AuditEventService auditEventService;

    /**
     * 构造实名认证服务。
     *
     * @param patientIdExtractor 统一身份提取
     * @param accountRepository  账户仓储
     * @param identityRepository 实名信息仓储
     * @param whitelistRepository 医院白名单只读仓储
     * @param cryptoService      摘要与加密
     * @param maskService        脱敏
     * @param rateLimitService   15 分钟窗口限流
     * @param auditEventService  审计
     */
    public IdentityVerificationServiceImpl(PatientIdExtractor patientIdExtractor,
                                           PatientAccountRepository accountRepository,
                                           PatientIdentityRepository identityRepository,
                                           HospitalIdentityWhitelistRepository whitelistRepository,
                                           CryptoService cryptoService,
                                           MaskService maskService,
                                           RateLimitService rateLimitService,
                                           AuditEventService auditEventService) {
        this.patientIdExtractor = patientIdExtractor;
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
        this.whitelistRepository = whitelistRepository;
        this.cryptoService = cryptoService;
        this.maskService = maskService;
        this.rateLimitService = rateLimitService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional
    public IdentityVerificationResponse verify(HttpServletRequest request,
                                                IdentityVerificationRequest body) {
        assertValidName(body.realName());
        String idCard = normalizeAndAssertChecksum(body.idCardNumber());
        TokenClaims claims = patientIdExtractor.extract(request);
        PatientAccount account = requireAccount(claims.patientId());

        // 幂等：已认证重复提交直接返回既有结果，不写重复认证记录（FR-017）
        if (account.authStatus() == AuthStatus.VERIFIED) {
            PatientIdentity existing = identityRepository.findByPatientId(account.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_UNAVAILABLE));
            return new IdentityVerificationResponse(AuthStatus.VERIFIED, true,
                    format(existing.verifiedAt()), existing.realNameMask(), existing.idCardMask());
        }

        rateLimitService.checkIdentityVerification(account.phoneHash());
        String idCardHash = cryptoService.idCardDigest(idCard);
        String maskedIdCard = maskService.maskIdCard(idCard);
        boolean matched = whitelistRepository.findEnabled(body.realName(), idCardHash).isPresent();
        if (!matched) {
            reject(account, maskedIdCard, "WHITELIST_MISS");
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        LocalDateTime now = LocalDateTime.now();
        String maskedName = maskService.maskName(body.realName());
        try {
            identityRepository.insert(new PatientIdentity(null, account.id(),
                    cryptoService.encrypt(maskService.utf8Bytes(body.realName())), maskedName,
                    idCardHash, cryptoService.encrypt(maskService.utf8Bytes(idCard)),
                    maskedIdCard, now, now, now, 0));
        } catch (BusinessException exception) {
            // uk_id_card_hash / uk_patient_id 冲突：证件已被其他账户绑定，统一文案
            reject(account, maskedIdCard, "ID_CARD_BOUND");
            throw exception;
        }
        if (accountRepository.updateAuthStatus(account.id(), STATUS_VERIFIED, now,
                account.version()) == 0) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        auditEventService.record(AuditEventType.IDENTITY_VERIFIED, "SUCCESS", account.id(),
                account.phoneMask(), maskedIdCard, null);
        return new IdentityVerificationResponse(AuthStatus.VERIFIED, true,
                now.format(FORMATTER), maskedName, maskedIdCard);
    }

    @Override
    @Transactional(readOnly = true)
    public IdentityStatusResponse status(HttpServletRequest request) {
        TokenClaims claims = patientIdExtractor.extract(request);
        PatientAccount account = requireAccount(claims.patientId());
        if (account.authStatus() != AuthStatus.VERIFIED) {
            return unverifiedStatus();
        }
        Optional<PatientIdentity> identity = identityRepository.findByPatientId(account.id());
        LocalDateTime verifiedAt = identity.map(PatientIdentity::verifiedAt)
                .orElse(account.verifiedAt());
        return new IdentityStatusResponse(AuthStatus.VERIFIED, true, format(verifiedAt),
                identity.map(PatientIdentity::realNameMask).orElse(null),
                identity.map(PatientIdentity::idCardMask).orElse(null));
    }

    private PatientAccount requireAccount(long patientId) {
        return accountRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private void reject(PatientAccount account, String maskedIdCard, String reasonCode) {
        auditEventService.record(AuditEventType.IDENTITY_REJECTED, "FAILURE", account.id(),
                account.phoneMask(), maskedIdCard, reasonCode);
    }

    private static IdentityStatusResponse unverifiedStatus() {
        return new IdentityStatusResponse(AuthStatus.UNVERIFIED, false, null, null, null);
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.format(FORMATTER);
    }

    private static void assertValidName(String name) {
        if (name == null || name.isBlank() || name.length() > 50
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_NAME_FORMAT);
        }
    }

    /** 校验位按 GB 11643 加权算法；小写 x 统一归一为大写后参与摘要。 */
    private static String normalizeAndAssertChecksum(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_ID_CARD_FORMAT);
        }
        String value = raw.toUpperCase(Locale.ROOT);
        if (!value.matches("^\\d{17}[0-9X]$")) {
            throw new BusinessException(ErrorCode.INVALID_ID_CARD_FORMAT);
        }
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (value.charAt(i) - '0') * WEIGHTS[i];
        }
        if (CHECK_CODES[sum % 11] != value.charAt(17)) {
            throw new BusinessException(ErrorCode.INVALID_ID_CARD_FORMAT);
        }
        return value;
    }
}
