package com.sdas.server.identity.controller;

import com.sdas.server.common.Result;
import com.sdas.server.identity.dto.PasswordResetRequest;
import com.sdas.server.identity.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 端点 6：重置密码（匿名 + CSRF 双重 Cookie 校验）。
 */
@RestController
@RequestMapping("/api/v1")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * 构造重置密码控制器。
     *
     * @param passwordResetService 重置密码服务
     */
    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * 重置登录密码。
     *
     * @param request 重置请求
     * @return 统一成功响应（data 为 null）
     */
    @PostMapping("/password-resets")
    public Result<Void> reset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.reset(request);
        return Result.success();
    }
}
