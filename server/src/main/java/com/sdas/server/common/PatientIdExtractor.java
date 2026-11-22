package com.sdas.server.common;

import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.TokenClaims;
import com.sdas.server.identity.support.RedisKeys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 统一 JWT 身份提取（原则 II）：Controller 禁止自行解析 token 或硬编码回退值。
 *
 * <p>登出 / 轮换 / 重置密码后，Redis 撤销标记使旧 access token 立即失效。</p>
 */
@Component
public class PatientIdExtractor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造身份提取器。
     *
     * @param jwtTokenService JWT 服务
     * @param redisTemplate   String Redis 模板
     */
    public PatientIdExtractor(JwtTokenService jwtTokenService,
                              StringRedisTemplate redisTemplate) {
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 从 Authorization 头提取身份并校验会话撤销状态。
     *
     * @param request HTTP 请求
     * @return 身份声明
     */
    public TokenClaims extract(HttpServletRequest request) {
        TokenClaims claims = parseBearer(request);
        Boolean revoked = redisTemplate.hasKey(RedisKeys.sessionRevoked(claims.sessionId()));
        if (Boolean.TRUE.equals(revoked)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return claims;
    }

    /**
     * 仅解析 JWT，不校验撤销标记；供安全退出的幂等语义使用（重复登出仍返回 200）。
     *
     * @param request HTTP 请求
     * @return 身份声明
     */
    public TokenClaims extractIgnoringRevocation(HttpServletRequest request) {
        return parseBearer(request);
    }

    private TokenClaims parseBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtTokenService.parse(header.substring(BEARER_PREFIX.length()));
    }
}
