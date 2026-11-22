package com.sdas.server.identity.service.impl;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.dto.PatientLoginRequest;
import com.sdas.server.identity.dto.PatientLoginResponse;
import com.sdas.server.identity.dto.RefreshTokenResponse;
import com.sdas.server.identity.entity.PatientAccount;
import com.sdas.server.identity.entity.PatientSession;
import com.sdas.server.identity.model.AccountStatus;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.model.AuthStatus;
import com.sdas.server.identity.model.SessionStatus;
import com.sdas.server.identity.repository.PatientAccountRepository;
import com.sdas.server.identity.repository.PatientSessionRepository;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.MaskService;
import com.sdas.server.identity.service.PatientSessionService;
import com.sdas.server.identity.service.RateLimitService;
import com.sdas.server.identity.service.TokenClaims;
import com.sdas.server.identity.support.CookieSupport;
import com.sdas.server.identity.support.RedisKeys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话服务：登录统一失败文案、退出幂等且旧凭证立即失效、续期凭证一次轮换。
 */
@Service
public class PatientSessionServiceImpl implements PatientSessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int TOKEN_BYTES = 32;

    private final PatientAccountRepository accountRepository;

    private final PatientSessionRepository sessionRepository;

    private final CryptoService cryptoService;

    private final MaskService maskService;

    private final JwtTokenService jwtTokenService;

    private final CookieSupport cookieSupport;

    private final RateLimitService rateLimitService;

    private final PatientIdExtractor patientIdExtractor;

    private final AuditEventService auditEventService;

    private final StringRedisTemplate redisTemplate;

    private final IdentityProperties properties;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 构造会话服务。
     *
     * @param accountRepository  账户仓储
     * @param sessionRepository  会话仓储
     * @param cryptoService      摘要工具
     * @param maskService        脱敏工具
     * @param jwtTokenService    JWT
     * @param cookieSupport      双 Cookie
     * @param rateLimitService   登录失败计数
     * @param patientIdExtractor 统一身份提取
     * @param auditEventService  审计
     * @param redisTemplate      Redis
     * @param properties         配置
     */
    public PatientSessionServiceImpl(PatientAccountRepository accountRepository,
                                     PatientSessionRepository sessionRepository,
                                     CryptoService cryptoService,
                                     MaskService maskService,
                                     JwtTokenService jwtTokenService,
                                     CookieSupport cookieSupport,
                                     RateLimitService rateLimitService,
                                     PatientIdExtractor patientIdExtractor,
                                     AuditEventService auditEventService,
                                     StringRedisTemplate redisTemplate,
                                     IdentityProperties properties) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.cryptoService = cryptoService;
        this.maskService = maskService;
        this.jwtTokenService = jwtTokenService;
        this.cookieSupport = cookieSupport;
        this.rateLimitService = rateLimitService;
        this.patientIdExtractor = patientIdExtractor;
        this.auditEventService = auditEventService;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    @Transactional
    public PatientLoginResponse login(PatientLoginRequest request, HttpServletResponse response) {
        String phoneDigest = cryptoService.phoneDigest(request.phone());
        Optional<PatientAccount> found = accountRepository.findByPhoneHash(phoneDigest);
        boolean valid = found.isPresent()
                && found.get().accountStatus() == AccountStatus.ACTIVE
                && passwordEncoder.matches(request.password(), found.get().passwordHash());
        if (!valid) {
            rateLimitService.shouldSkipPasswordCheck(phoneDigest);
            auditEventService.record(AuditEventType.PATIENT_LOGIN_FAILED, "FAILURE",
                    null, maskService.maskPhone(request.phone()), null, null);
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        PatientAccount account = found.get();
        LocalDateTime now = LocalDateTime.now();
        long refreshTtl = properties.jwt().refreshTokenTtlSeconds();
        String refreshToken = randomToken();
        long sessionId = sessionRepository.insert(new PatientSession(null, account.id(),
                cryptoService.codeDigest(refreshToken), SessionStatus.ACTIVE, null,
                now.plusSeconds(refreshTtl), null, now, now, 0));
        String accessToken = jwtTokenService.issue(account.id(), sessionId);
        cookieSupport.write(response, refreshToken, randomToken(), Duration.ofSeconds(refreshTtl));
        auditEventService.record(AuditEventType.PATIENT_LOGIN_SUCCEEDED, "SUCCESS",
                account.id(), account.phoneMask(), null, null);
        boolean allowed = account.authStatus() == AuthStatus.VERIFIED;
        return new PatientLoginResponse(account.id(), account.phoneMask(),
                account.authStatus(), allowed, accessToken,
                jwtTokenService.accessTokenTtlSeconds());
    }

    @Override
    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        TokenClaims claims = patientIdExtractor.extractIgnoringRevocation(request);
        sessionRepository.revokeById(claims.sessionId());
        markRevoked(claims.sessionId());
        cookieSupport.clear(response);
        auditEventService.record(AuditEventType.PATIENT_LOGGED_OUT, "SUCCESS",
                claims.patientId(), null, null, null);
    }

    @Override
    @Transactional
    public RefreshTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readRefreshCookie(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Optional<PatientSession> found = sessionRepository.findByRefreshTokenHash(
                cryptoService.codeDigest(refreshToken));
        if (found.isEmpty() || found.get().sessionStatus() != SessionStatus.ACTIVE
                || found.get().expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        PatientSession old = found.get();
        if (sessionRepository.revoke(old.id(), old.version()) == 0) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        markRevoked(old.id());
        LocalDateTime now = LocalDateTime.now();
        long refreshTtl = properties.jwt().refreshTokenTtlSeconds();
        String newRefresh = randomToken();
        long newSessionId = sessionRepository.insert(new PatientSession(null, old.patientId(),
                cryptoService.codeDigest(newRefresh), SessionStatus.ACTIVE, null,
                now.plusSeconds(refreshTtl), null, now, now, 0));
        String accessToken = jwtTokenService.issue(old.patientId(), newSessionId);
        cookieSupport.rotateRefresh(response, newRefresh, Duration.ofSeconds(refreshTtl));
        auditEventService.record(AuditEventType.SESSION_REFRESHED, "SUCCESS",
                old.patientId(), null, null, null);
        return new RefreshTokenResponse(accessToken, jwtTokenService.accessTokenTtlSeconds());
    }

    private void markRevoked(long sessionId) {
        redisTemplate.opsForValue().set(RedisKeys.sessionRevoked(sessionId), "1",
                Duration.ofSeconds(jwtTokenService.accessTokenTtlSeconds()));
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CookieSupport.REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
