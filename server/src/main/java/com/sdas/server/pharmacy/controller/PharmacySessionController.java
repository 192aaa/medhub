package com.sdas.server.pharmacy.controller;

import com.sdas.server.common.Result;
import com.sdas.server.pharmacy.dto.PharmacyLoginRequest;
import com.sdas.server.pharmacy.dto.PharmacyLoginResponse;
import com.sdas.server.pharmacy.service.PharmacyAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 药店登录端点（004 端点 1）：不校验令牌，但强制 CSRF 双重 Cookie。 */
@RestController
@RequestMapping("/api/v1/pharmacy/sessions")
public class PharmacySessionController {

    private final PharmacyAuthService authService;

    /**
     * 构造控制器。
     *
     * @param authService 药店登录服务
     */
    public PharmacySessionController(PharmacyAuthService authService) {
        this.authService = authService;
    }

    /**
     * 药店登录。
     *
     * @param request 登录请求
     * @return 访问令牌与药店信息
     */
    @PostMapping
    public Result<PharmacyLoginResponse> login(@Valid @RequestBody PharmacyLoginRequest request) {
        return Result.success(authService.login(request));
    }
}
