package com.sdas.server.identity.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.config.CookieProperties;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.dto.PatientLoginRequest;
import com.sdas.server.identity.dto.PatientLoginResponse;
import com.sdas.server.identity.entity.PatientAccount;
import com.sdas.server.identity.entity.PatientSession;
import com.sdas.server.identity.model.AccountStatus;
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
import com.sdas.server.identity.service.impl.PatientSessionServiceImpl;
import com.sdas.server.identity.support.CookieSupport;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import jakarta.servlet.http.Cookie;

/**
 * T062：续期凭证只存摘要、轮换原子性（版本冲突即失败）、过期会话失败、缺 Cookie 401。
 */
class PatientSessionServiceTest {

    private static final String PASSWORD = "abc12345";

    private PatientAccountRepository accountRepository;

    private PatientSessionRepository sessionRepository;

    private CryptoService cryptoService;

    private JwtTokenService jwtTokenService;

    private PatientSessionService service;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = mock(IdentityProperties.class);
        IdentityProperties.Jwt jwt = mock(IdentityProperties.Jwt.class);
        when(properties.jwt()).thenReturn(jwt);
        when(jwt.refreshTokenTtlSeconds()).thenReturn(604_800L);

        CookieProperties cookieProperties = mock(CookieProperties.class);
        when(cookieProperties.secure()).thenReturn(false);

        accountRepository = mock(PatientAccountRepository.class);
        sessionRepository = mock(PatientSessionRepository.class);
        cryptoService = mock(CryptoService.class);
        jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(7200L);
        when(jwtTokenService.issue(anyLong(), anyLong())).thenReturn("ACCESS");
        when(cryptoService.phoneDigest(anyString())).thenReturn("PHONE_DIGEST");
        when(cryptoService.codeDigest(anyString()))
                .thenAnswer(invocation -> "digest:" + invocation.getArgument(0));
        when(sessionRepository.insert(any(PatientSession.class))).thenReturn(99L);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        service = new PatientSessionServiceImpl(accountRepository, sessionRepository,
                cryptoService, mock(MaskService.class), jwtTokenService,
                new CookieSupport(cookieProperties), mock(RateLimitService.class),
                mock(PatientIdExtractor.class), mock(AuditEventService.class),
                redisTemplate, properties);
    }

    @Test
    void loginStoresOnlyDigestOfRefreshToken() {
        when(accountRepository.findByPhoneHash("PHONE_DIGEST"))
                .thenReturn(Optional.of(account()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        PatientLoginResponse result = service.login(
                new PatientLoginRequest("13800138000", PASSWORD), response);

        ArgumentCaptor<PatientSession> captor = ArgumentCaptor.forClass(PatientSession.class);
        verify(sessionRepository).insert(captor.capture());
        String storedHash = captor.getValue().refreshTokenHash();
        String plaintext = refreshCookie(response);

        assertNotNull(plaintext);
        assertTrue(storedHash.startsWith("digest:"));
        assertNotEquals(plaintext, storedHash);
        assertEquals("digest:" + plaintext, storedHash);
        assertEquals(7200L, result.accessTokenExpiresInSeconds());
    }

    @Test
    void rotationVersionConflictFails() {
        when(sessionRepository.findByRefreshTokenHash(anyString()))
                .thenReturn(Optional.of(session(LocalDateTime.now().plusDays(1))));
        when(sessionRepository.revoke(anyLong(), anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refresh(requestWithRefresh(), new MockHttpServletResponse()));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
    }

    @Test
    void expiredSessionFails() {
        when(sessionRepository.findByRefreshTokenHash(anyString()))
                .thenReturn(Optional.of(session(LocalDateTime.now().minusMinutes(1))));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refresh(requestWithRefresh(), new MockHttpServletResponse()));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
    }

    @Test
    void missingRefreshCookieReturnsUnauthorized() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refresh(new MockHttpServletRequest(),
                        new MockHttpServletResponse()));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    private PatientAccount account() {
        String hash = new BCryptPasswordEncoder(4).encode(PASSWORD);
        return new PatientAccount(1L, "PHONE_DIGEST", new byte[0], "138****8000",
                hash, AuthStatus.UNVERIFIED, AccountStatus.ACTIVE,
                LocalDateTime.now(), null, LocalDateTime.now(), LocalDateTime.now(), 0);
    }

    private PatientSession session(LocalDateTime expiresAt) {
        return new PatientSession(7L, 1L, "digest:refreshed", SessionStatus.ACTIVE,
                null, expiresAt, null, LocalDateTime.now(), LocalDateTime.now(), 0);
    }

    private MockHttpServletRequest requestWithRefresh() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieSupport.REFRESH_COOKIE, "refreshed"));
        return request;
    }

    private String refreshCookie(MockHttpServletResponse response) {
        for (String header : response.getHeaders("Set-Cookie")) {
            if (header.startsWith(CookieSupport.REFRESH_COOKIE + "=")) {
                String value = header.substring(CookieSupport.REFRESH_COOKIE.length() + 1);
                int semicolon = value.indexOf(';');
                return semicolon < 0 ? value : value.substring(0, semicolon);
            }
        }
        return null;
    }
}
