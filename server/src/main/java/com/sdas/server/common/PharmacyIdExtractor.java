package com.sdas.server.common;

import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.PharmacyTokenClaims;
import com.sdas.server.identity.support.RedisKeys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 统一药店 JWT 身份提取（原则 II）：Controller 禁止自行解析 token 或硬编码回退值。
 *
 * <p>镜像 {@link DoctorIdExtractor}，复用 Redis 撤销校验；角色不匹配（患者/医生 token）统一抛
 * {@link ErrorCode#PHARMACY_ROLE_REQUIRED}。</p>
 */
@Component
public class PharmacyIdExtractor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造药店身份提取器。
     *
     * @param jwtTokenService JWT 服务
     * @param redisTemplate   String Redis 模板
     */
    public PharmacyIdExtractor(JwtTokenService jwtTokenService,
                               StringRedisTemplate redisTemplate) {
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 从 Authorization 头提取药店身份并校验会话撤销状态。
     *
     * @param request HTTP 请求
     * @return 药店身份声明
     */
    public PharmacyTokenClaims extract(HttpServletRequest request) {
        PharmacyTokenClaims claims = parseBearer(request);
        Boolean revoked = redisTemplate.hasKey(RedisKeys.sessionRevoked(claims.sessionId()));
        if (Boolean.TRUE.equals(revoked)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return claims;
    }

    private PharmacyTokenClaims parseBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.PHARMACY_ROLE_REQUIRED);
        }
        return jwtTokenService.parsePharmacy(header.substring(BEARER_PREFIX.length()));
    }
}
