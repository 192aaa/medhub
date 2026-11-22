package com.sdas.server.identity.service.impl;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.dto.PasswordResetRequest;
import com.sdas.server.identity.entity.PatientAccount;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.model.VerificationPurpose;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.repository.PatientSessionRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.PasswordResetService;
import com.sdas.server.identity.service.VerificationCodeService;
import com.sdas.server.identity.support.RedisKeys;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 重置密码事务：验证码消费 + 密码更新 + 撤销全部会话 + 清理验证码 + 审计，整体回滚。
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final PatientAccountRepository accountRepository;

    private final PatientSessionRepository sessionRepository;

    private final VerificationCodeService verificationCodeService;

    private final CryptoService cryptoService;

    private final AuditEventService auditEventService;

    private final StringRedisTemplate redisTemplate;

    private final JwtTokenService jwtTokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 构造重置密码服务。
     *
     * @param accountRepository          账户仓储
     * @param sessionRepository          会话仓储
     * @param verificationCodeService    验证码服务
     * @param cryptoService              摘要工具
     * @param auditEventService          审计服务
     * @param redisTemplate             Redis（写入会话撤销标记）
     * @param jwtTokenService           access token 有效期来源
     */
    public PasswordResetServiceImpl(PatientAccountRepository accountRepository,
                                    PatientSessionRepository sessionRepository,
                                    VerificationCodeService verificationCodeService,
                                    CryptoService cryptoService,
                                    AuditEventService auditEventService,
                                    StringRedisTemplate redisTemplate,
                                    JwtTokenService jwtTokenService) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.verificationCodeService = verificationCodeService;
        this.cryptoService = cryptoService;
        this.auditEventService = auditEventService;
        this.redisTemplate = redisTemplate;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    @Transactional
    public void reset(PasswordResetRequest request) {
        assertPasswordRule(request.newPassword());
        String phoneHash = cryptoService.phoneDigest(request.phone());
        PatientAccount account = accountRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_FAILED));
        verificationCodeService.consume(VerificationPurpose.RESET_PASSWORD,
                request.phone(), request.verificationCode());

        int rows = accountRepository.updatePasswordHash(
                account.id(), passwordEncoder.encode(request.newPassword()), account.version());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        List<Long> activeSessionIds = sessionRepository.findActiveSessionIds(account.id());
        sessionRepository.revokeAllByPatientId(account.id());
        // 与 logout 的撤销标记保持同一有效期：覆盖旧 access token 的剩余寿命即可
        Duration revokedTtl = Duration.ofSeconds(jwtTokenService.accessTokenTtlSeconds());
        for (Long sessionId : activeSessionIds) {
            redisTemplate.opsForValue().set(RedisKeys.sessionRevoked(sessionId), "1", revokedTtl);
        }
        verificationCodeService.clear(VerificationPurpose.RESET_PASSWORD, request.phone());
        auditEventService.record(AuditEventType.PASSWORD_RESET, "SUCCESS", account.id(),
                account.phoneMask(), null, null);
    }

    private void assertPasswordRule(String password) {
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }
}
