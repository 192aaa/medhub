package com.sdas.server.identity.controller;

import com.sdas.server.common.Result;
import com.sdas.server.identity.dto.PatientLoginRequest;
import com.sdas.server.identity.dto.PatientLoginResponse;
import com.sdas.server.identity.dto.RefreshTokenResponse;
import com.sdas.server.identity.service.PatientSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 端点 3/4/5：登录、安全退出、续期凭证轮换。
 */
@RestController
@RequestMapping("/api/v1/patient-sessions")
public class PatientSessionController {

    private final PatientSessionService service;

    /**
     * 构造控制器。
     *
     * @param service 会话服务
     */
    public PatientSessionController(PatientSessionService service) {
        this.service = service;
    }

    /**
     * 端点 3：手机号密码登录。
     *
     * @param request  登录请求
     * @param response 下发双 Cookie
     * @return 登录结果
     */
    @PostMapping
    public Result<PatientLoginResponse> login(@Valid @RequestBody PatientLoginRequest request,
                                              HttpServletResponse response) {
        return Result.success(service.login(request, response));
    }

    /**
     * 端点 4：安全退出（幂等）。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @return data 为 null 的统一响应
     */
    @DeleteMapping("/current")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        service.logout(request, response);
        return Result.success();
    }

    /**
     * 端点 5：一次性轮换续期凭证。
     *
     * @param request  HTTP 请求（sdas_refresh Cookie）
     * @param response HTTP 响应
     * @return 新 access token
     */
    @PutMapping("/current/refresh-token")
    public Result<RefreshTokenResponse> refresh(HttpServletRequest request,
                                                HttpServletResponse response) {
        return Result.success(service.refresh(request, response));
    }
}
