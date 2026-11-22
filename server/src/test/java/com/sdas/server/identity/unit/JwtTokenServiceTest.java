package com.sdas.server.identity.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.impl.JwtTokenServiceImpl;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T017 前置单测：JWT 签发 / 解析 / 过期 / 签名非法 / 缺 sessionId / TTL 边界。
 */
class JwtTokenServiceTest {

    private static final String SECRET =
            "test-only-jwt-secret-please-replace-in-production-000000";

    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        service = serviceWithTtl(7200);
    }

    @Test
    void issueThenParseReturnsClaims() {
        String token = service.issue(10001L, 20001L);
        var claims = service.parse(token);
        assertEquals(10001L, claims.patientId());
        assertEquals(20001L, claims.sessionId());
    }

    @Test
    void accessTokenTtlIs7200Seconds() {
        assertEquals(7200L, service.accessTokenTtlSeconds());
    }

    @Test
    void expiredTokenIsUnauthorized() {
        JwtTokenService expiring = serviceWithTtl(-1);
        String token = expiring.issue(10001L, 20001L);
        BusinessException exception =
                assertThrows(BusinessException.class, () -> expiring.parse(token));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void tamperedSignatureIsUnauthorized() {
        String token = service.issue(10001L, 20001L);
        String tampered = token.substring(0, token.length() - 2) + "aa";
        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.parse(tampered));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void missingSessionIdIsUnauthorized() {
        SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("10001")
                .claim("patientId", 10001L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.parse(token));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    private JwtTokenService serviceWithTtl(long ttlSeconds) {
        IdentityProperties properties = mock(IdentityProperties.class);
        when(properties.jwt()).thenReturn(
                new IdentityProperties.Jwt(ttlSeconds, 604800L, SECRET));
        return new JwtTokenServiceImpl(properties);
    }
}
