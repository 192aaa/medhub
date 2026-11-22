package com.sdas.server.identity.controller;

import com.sdas.server.common.Result;
import com.sdas.server.identity.dto.IdentityVerificationRequest;
import com.sdas.server.identity.dto.IdentityVerificationResponse;
import com.sdas.server.identity.service.IdentityVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 端点 7：提交实名认证；身份取自 PatientIdExtractor，Controller 不解析 JWT。
 */
@RestController
@RequestMapping("/api/v1")
public class IdentityVerificationController {

    private final IdentityVerificationService identityVerificationService;

    /**
     * 构造控制器。
     *
     * @param identityVerificationService 实名认证服务
     */
    public IdentityVerificationController(IdentityVerificationService identityVerificationService) {
        this.identityVerificationService = identityVerificationService;
    }

    /**
     * 提交实名认证（需 Bearer 凭证 + CSRF 双重 Cookie）。
     *
     * @param body        认证请求
     * @param httpRequest HTTP 请求
     * @return 认证结果
     */
    @PostMapping("/identity-verifications")
    public Result<IdentityVerificationResponse> verify(
            @Valid @RequestBody IdentityVerificationRequest body,
            HttpServletRequest httpRequest) {
        return Result.success(identityVerificationService.verify(httpRequest, body));
    }
}
