package com.sdas.server.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 访问日志过滤器：记录脱敏后的请求路径、状态码、耗时与 traceId。
 *
 * <p>不记录 header、body、query string，避免密码、验证码、Token 等敏感信息落盘。</p>
 */
@Component
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessLogFilter.class);

    private static final String TRACE_ID = "traceId";

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 执行过滤并记录访问日志。
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param chain    过滤链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = System.currentTimeMillis() - start;
            String method = request.getMethod();
            String uri = LogMasker.mask(request.getRequestURI());
            int status = response.getStatus();
            if (status >= 500) {
                LOGGER.error("{} {} status={} elapsedMs={}", method, uri, status, elapsedMs);
            } else if (status >= 400) {
                LOGGER.warn("{} {} status={} elapsedMs={}", method, uri, status, elapsedMs);
            } else {
                LOGGER.info("{} {} status={} elapsedMs={}", method, uri, status, elapsedMs);
            }
            MDC.remove(TRACE_ID);
        }
    }
}
