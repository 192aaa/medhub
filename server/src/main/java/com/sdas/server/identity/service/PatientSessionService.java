package com.sdas.server.identity.service;

import com.sdas.server.identity.dto.PatientLoginRequest;
import com.sdas.server.identity.dto.PatientLoginResponse;
import com.sdas.server.identity.dto.RefreshTokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录 / 安全退出 / 续期凭证轮换。
 */
public interface PatientSessionService {

    /**
     * 手机号密码登录，失败一律返回统一模糊文案。
     *
     * @param request  登录请求
     * @param response 下发双 Cookie
     * @return 登录结果
     */
    PatientLoginResponse login(PatientLoginRequest request, HttpServletResponse response);

    /**
     * 安全退出：撤销当前会话、写 Redis 撤销标记、清除 Cookie；重复调用仍成功。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    void logout(HttpServletRequest request, HttpServletResponse response);

    /**
     * 一次性轮换续期凭证；旧凭证立即失效，并发仅一个成功。
     *
     * @param request  HTTP 请求（sdas_refresh Cookie）
     * @param response HTTP 响应（新 Cookie）
     * @return 新 access token
     */
    RefreshTokenResponse refresh(HttpServletRequest request, HttpServletResponse response);
}
