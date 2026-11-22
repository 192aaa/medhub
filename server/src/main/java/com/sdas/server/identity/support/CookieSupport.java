package com.sdas.server.identity.support;

import com.sdas.server.config.CookieProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 双 Cookie 下发与清除（契约 contracts/README.md 第 5 节）。
 *
 * <p>sdas_refresh：HttpOnly；sdas_csrf：非 HttpOnly（前端读取后放入 X-CSRF-Token）。</p>
 */
@Component
public class CookieSupport {

    /** 续期凭证 Cookie 名。 */
    public static final String REFRESH_COOKIE = "sdas_refresh";

    /** CSRF Cookie 名。 */
    public static final String CSRF_COOKIE = "sdas_csrf";

    private static final String REFRESH_PATH = "/api/v1/patient-sessions";

    private static final String CSRF_PATH = "/api/v1";

    private final CookieProperties properties;

    /**
     * 构造 Cookie 工具。
     *
     * @param properties Cookie 安全配置
     */
    public CookieSupport(CookieProperties properties) {
        this.properties = properties;
    }

    /**
     * 下发续期凭证与 CSRF Cookie。
     *
     * @param response     HTTP 响应
     * @param refreshToken 续期凭证明文（仅用于 Set-Cookie，不入库明文）
     * @param csrfToken    CSRF 随机值
     * @param maxAge       续期凭证有效期
     */
    public void write(HttpServletResponse response, String refreshToken,
                      String csrfToken, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(
                REFRESH_COOKIE, refreshToken, true, REFRESH_PATH, maxAge).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, build(
                CSRF_COOKIE, csrfToken, false, CSRF_PATH, maxAge).toString());
    }

    /**
     * 轮换续期凭证（CSRF Cookie 保持不变）。
     *
     * @param response     HTTP 响应
     * @param refreshToken 新续期凭证
     * @param maxAge       有效期
     */
    public void rotateRefresh(HttpServletResponse response,
                              String refreshToken, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(
                REFRESH_COOKIE, refreshToken, true, REFRESH_PATH, maxAge).toString());
    }

    /**
     * 安全退出时清除两个 Cookie。
     *
     * @param response HTTP 响应
     */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(
                REFRESH_COOKIE, "", true, REFRESH_PATH, Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, build(
                CSRF_COOKIE, "", false, CSRF_PATH, Duration.ZERO).toString());
    }

    private ResponseCookie build(String name, String value, boolean httpOnly,
                                 String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.secure())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
