package com.sdas.server.identity.controller;

import com.sdas.server.common.Result;
import com.sdas.server.identity.dto.VerificationCodeSendRequest;
import com.sdas.server.identity.dto.VerificationCodeSendResponse;
import com.sdas.server.identity.service.VerificationCodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 端点 1：签发验证码（CSRF 由 CsrfCookieFilter 统一校验）。
 */
@RestController
@RequestMapping("/api/v1")
public class VerificationCodeController {

    private final VerificationCodeService service;

    /**
     * 构造控制器。
     *
     * @param service 验证码服务
     */
    public VerificationCodeController(VerificationCodeService service) {
        this.service = service;
    }

    /**
     * 签发验证码。
     *
     * @param request 请求体
     * @return 统一响应，data 不含验证码
     */
    @PostMapping("/verification-codes")
    public Result<VerificationCodeSendResponse> send(
            @Valid @RequestBody VerificationCodeSendRequest request) {
        return Result.success(service.send(request));
    }
}
