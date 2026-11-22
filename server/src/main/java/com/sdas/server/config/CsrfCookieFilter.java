package com.sdas.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.Result;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.support.CookieSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 双重 Cookie CSRF 校验（原则 IV）。
 *
 * <p>仅拦截 /api/v1/ 下的 POST/PUT/PATCH/DELETE；/api/health 等既有端点不纳入。</p>
 */
@Component
@Order(1)
public class CsrfCookieFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final String CSRF_HEADER = "X-CSRF-Token";

    private static final String API_PREFIX = "/api/v1/";

    private final CryptoService cryptoService;

    private final ObjectMapper objectMapper;

    /**
     * 构造 CSRF 过滤器。
     *
     * @param cryptoService 常量时间比较工具
     * @param objectMapper  JSON 序列化器
     */
    public CsrfCookieFilter(CryptoService cryptoService, ObjectMapper objectMapper) {
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!requiresCsrf(request)) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(CSRF_HEADER);
        String cookie = readCsrfCookie(request);
        if (header == null || cookie == null
                || !cryptoService.constantTimeEquals(header, cookie)) {
            response.setStatus(ErrorCode.CSRF_TOKEN_MISMATCH.getCode());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.<Void>failure(ErrorCode.CSRF_TOKEN_MISMATCH)));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean requiresCsrf(HttpServletRequest request) {
        return UNSAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))
                && request.getRequestURI().startsWith(API_PREFIX);
    }

    private String readCsrfCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CookieSupport.CSRF_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
