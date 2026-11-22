package com.sdas.server.pharmacy.service.impl;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.pharmacy.dto.PharmacyLoginRequest;
import com.sdas.server.pharmacy.dto.PharmacyLoginResponse;
import com.sdas.server.pharmacy.entity.PharmacyAccount;
import com.sdas.server.pharmacy.model.PharmacyAccountStatus;
import com.sdas.server.pharmacy.repository.PharmacyAccountRepository;
import com.sdas.server.pharmacy.service.PharmacyAuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 药店登录实现。
 *
 * <p>审计主体为 {@code username} 的 SHA-256 前 16 位十六进制（不存明文），复用
 * {@code security_audit_event.reason_code} 字段承载——审计表无药店主体列，且该字段在登录事件中
 * 不承载其他语义。</p>
 */
@Service
public class PharmacyAuthServiceImpl implements PharmacyAuthService {

    private static final int DIGEST_LENGTH = 16;

    private final PharmacyAccountRepository accountRepository;

    private final JwtTokenService jwtTokenService;

    private final AuditEventService auditEventService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 构造药店登录服务。
     *
     * @param accountRepository 药店账号仓储
     * @param jwtTokenService   JWT 服务
     * @param auditEventService 审计服务
     */
    public PharmacyAuthServiceImpl(PharmacyAccountRepository accountRepository,
                                   JwtTokenService jwtTokenService,
                                   AuditEventService auditEventService) {
        this.accountRepository = accountRepository;
        this.jwtTokenService = jwtTokenService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional(readOnly = true)
    public PharmacyLoginResponse login(PharmacyLoginRequest request) {
        Optional<PharmacyAccount> found = accountRepository.findByUsername(request.username());
        boolean valid = found.isPresent()
                && found.get().status() == PharmacyAccountStatus.ACTIVE
                && passwordEncoder.matches(request.password(), found.get().passwordHash());
        if (!valid) {
            auditEventService.record(AuditEventType.PHARMACY_LOGIN_FAILED, "FAILURE",
                    null, null, null, digest(request.username()));
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        PharmacyAccount account = found.get();
        long sessionId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        String accessToken = jwtTokenService.issuePharmacy(account.id(), sessionId);
        auditEventService.record(AuditEventType.PHARMACY_LOGIN_SUCCESS, "SUCCESS",
                null, null, null, digest(request.username()));
        return new PharmacyLoginResponse(accessToken, account.id(), account.nameMask(),
                account.pharmacyName(), jwtTokenService.accessTokenTtlSeconds());
    }

    private static String digest(String username) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(
                    sha256.digest(username.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, DIGEST_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
