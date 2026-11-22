package com.sdas.server.identity.service.impl;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.service.DoctorTokenClaims;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.PharmacyTokenClaims;
import com.sdas.server.identity.service.TokenClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * HS256 JWT 实现，密钥与 TTL 只从 IdentityProperties 注入。
 *
 * <p>research.md R-07：access token 2 小时，载荷禁止携带手机号 / 身份证号。</p>
 */
@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private final IdentityProperties properties;

    /**
     * 构造 JWT 服务。
     *
     * @param properties 患者身份模块配置
     */
    public JwtTokenServiceImpl(IdentityProperties properties) {
        this.properties = properties;
    }

    @Override
    public String issue(long patientId, long sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(patientId))
                .claim("patientId", patientId)
                .claim("sessionId", sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.jwt().accessTokenTtlSeconds())))
                .signWith(secretKey())
                .compact();
    }

    @Override
    public TokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number patientId = claims.get("patientId", Number.class);
            Number sessionId = claims.get("sessionId", Number.class);
            if (patientId == null || sessionId == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return new TokenClaims(patientId.longValue(), sessionId.longValue());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    @Override
    public String issueDoctor(long doctorId, long sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(doctorId))
                .claim("doctorId", doctorId)
                .claim("sessionId", sessionId)
                .claim("role", "DOCTOR")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.jwt().accessTokenTtlSeconds())))
                .signWith(secretKey())
                .compact();
    }

    @Override
    public DoctorTokenClaims parseDoctor(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number doctorId = claims.get("doctorId", Number.class);
            Number sessionId = claims.get("sessionId", Number.class);
            String role = claims.get("role", String.class);
            if (doctorId == null || sessionId == null || !"DOCTOR".equals(role)) {
                throw new BusinessException(ErrorCode.DOCTOR_ROLE_REQUIRED);
            }
            return new DoctorTokenClaims(doctorId.longValue(), sessionId.longValue());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.DOCTOR_ROLE_REQUIRED);
        }
    }

    @Override
    public String issuePharmacy(long pharmacyId, long sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(pharmacyId))
                .claim("pharmacyId", pharmacyId)
                .claim("sessionId", sessionId)
                .claim("role", "PHARMACY")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.jwt().accessTokenTtlSeconds())))
                .signWith(secretKey())
                .compact();
    }

    @Override
    public PharmacyTokenClaims parsePharmacy(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number pharmacyId = claims.get("pharmacyId", Number.class);
            Number sessionId = claims.get("sessionId", Number.class);
            String role = claims.get("role", String.class);
            if (pharmacyId == null || sessionId == null || !"PHARMACY".equals(role)) {
                throw new BusinessException(ErrorCode.PHARMACY_ROLE_REQUIRED);
            }
            return new PharmacyTokenClaims(pharmacyId.longValue(), sessionId.longValue());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PHARMACY_ROLE_REQUIRED);
        }
    }

    @Override
    public long accessTokenTtlSeconds() {
        return properties.jwt().accessTokenTtlSeconds();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }
}
