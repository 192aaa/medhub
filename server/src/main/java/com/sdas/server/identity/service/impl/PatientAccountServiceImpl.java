package com.sdas.server.identity.service.impl;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.dto.PatientRegisterRequest;
import com.sdas.server.identity.dto.PatientRegisterResponse;
import com.sdas.server.identity.entity.PatientAccount;
import com.sdas.server.identity.entity.PatientAgreementConsent;
import com.sdas.server.identity.entity.PatientSession;
import com.sdas.server.identity.model.AccountStatus;
import com.sdas.server.identity.model.AgreementConsentStatus;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.model.AuthStatus;
import com.sdas.server.identity.model.SessionStatus;
import com.sdas.server.identity.model.VerificationPurpose;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.repository.PatientAgreementConsentRepository;
import com.sdas.server.identity.repository.PatientSessionRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.IdempotencyService;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.MaskService;
import com.sdas.server.identity.service.PatientAccountService;
import com.sdas.server.identity.service.VerificationCodeService;
import com.sdas.server.identity.support.CookieSupport;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册事务：账户 + 协议同意 + 会话 + 审计在同一事务，失败整体回滚。
 */
@Service
public class PatientAccountServiceImpl implements PatientAccountService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int TOKEN_BYTES = 32;

    private final PatientAccountRepository accountRepository;

    private final PatientAgreementConsentRepository consentRepository;

    private final PatientSessionRepository sessionRepository;

    private final VerificationCodeService verificationCodeService;

    private final CryptoService cryptoService;

    private final MaskService maskService;

    private final JwtTokenService jwtTokenService;

    private final CookieSupport cookieSupport;

    private final IdempotencyService idempotencyService;

    private final AuditEventService auditEventService;

    private final IdentityProperties properties;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 构造注册服务。
     *
     * @param accountRepository       账户仓储
     * @param consentRepository       协议同意仓储
     * @param sessionRepository       会话仓储
     * @param verificationCodeService 验证码服务
     * @param cryptoService           摘要与加密
     * @param maskService             脱敏
     * @param jwtTokenService         JWT
     * @param cookieSupport           双 Cookie 下发
     * @param idempotencyService      幂等执行器
     * @param auditEventService       审计
     * @param properties              配置
     */
    public PatientAccountServiceImpl(PatientAccountRepository accountRepository,
                                     PatientAgreementConsentRepository consentRepository,
                                     PatientSessionRepository sessionRepository,
                                     VerificationCodeService verificationCodeService,
                                     CryptoService cryptoService,
                                     MaskService maskService,
                                     JwtTokenService jwtTokenService,
                                     CookieSupport cookieSupport,
                                     IdempotencyService idempotencyService,
                                     AuditEventService auditEventService,
                                     IdentityProperties properties) {
        this.accountRepository = accountRepository;
        this.consentRepository = consentRepository;
        this.sessionRepository = sessionRepository;
        this.verificationCodeService = verificationCodeService;
        this.cryptoService = cryptoService;
        this.maskService = maskService;
        this.jwtTokenService = jwtTokenService;
        this.cookieSupport = cookieSupport;
        this.idempotencyService = idempotencyService;
        this.auditEventService = auditEventService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public PatientRegisterResponse register(PatientRegisterRequest request, String idempotencyKey,
                                            HttpServletResponse response) {
        assertAgreement(request);
        assertPasswordRule(request.password());
        return idempotencyService.execute(idempotencyKey, PatientRegisterResponse.class,
                () -> doRegister(request, response));
    }

    private PatientRegisterResponse doRegister(PatientRegisterRequest request,
                                               HttpServletResponse response) {
        String phoneHash = cryptoService.phoneDigest(request.phone());
        if (accountRepository.findByPhoneHash(phoneHash).isPresent()) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        verificationCodeService.consume(VerificationPurpose.REGISTER,
                request.phone(), request.verificationCode());

        LocalDateTime now = LocalDateTime.now();
        String maskedPhone = maskService.maskPhone(request.phone());
        long patientId = accountRepository.insert(new PatientAccount(null, phoneHash,
                cryptoService.encrypt(maskService.utf8Bytes(request.phone())), maskedPhone,
                passwordEncoder.encode(request.password()), AuthStatus.UNVERIFIED,
                AccountStatus.ACTIVE, now, null, now, now, 0));
        consentRepository.insert(new PatientAgreementConsent(null, patientId,
                request.agreementVersion(), AgreementConsentStatus.AGREED, now, now, now, 0));

        long refreshTtl = properties.jwt().refreshTokenTtlSeconds();
        String refreshToken = randomToken();
        long sessionId = sessionRepository.insert(new PatientSession(null, patientId,
                cryptoService.codeDigest(refreshToken), SessionStatus.ACTIVE, null,
                now.plusSeconds(refreshTtl), null, now, now, 0));
        String accessToken = jwtTokenService.issue(patientId, sessionId);
        cookieSupport.write(response, refreshToken, randomToken(), Duration.ofSeconds(refreshTtl));
        auditEventService.record(AuditEventType.PATIENT_REGISTERED, "SUCCESS", patientId,
                maskedPhone, null, null);
        return new PatientRegisterResponse(patientId, maskedPhone, AuthStatus.UNVERIFIED, false,
                accessToken, jwtTokenService.accessTokenTtlSeconds(), now.format(FORMATTER),
                request.agreementVersion());
    }

    private void assertAgreement(PatientRegisterRequest request) {
        if (!Boolean.TRUE.equals(request.agreementAccepted())
                || !properties.agreement().version().equals(request.agreementVersion())) {
            throw new BusinessException(ErrorCode.AGREEMENT_NOT_ACCEPTED);
        }
    }

    private void assertPasswordRule(String password) {
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
