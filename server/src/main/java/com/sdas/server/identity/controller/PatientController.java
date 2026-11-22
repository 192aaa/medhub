package com.sdas.server.identity.controller;

import com.sdas.server.common.Result;
import com.sdas.server.identity.dto.IdentityStatusResponse;
import com.sdas.server.identity.dto.PatientRegisterRequest;
import com.sdas.server.identity.dto.PatientRegisterResponse;
import com.sdas.server.identity.service.IdentityVerificationService;
import com.sdas.server.identity.service.PatientAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者端点：注册（端点 2）与实名认证状态查询（端点 8）。
 */
@RestController
@RequestMapping("/api/v1")
public class PatientController {

    private final PatientAccountService patientAccountService;

    private final IdentityVerificationService identityVerificationService;

    /**
     * 构造控制器。
     *
     * @param patientAccountService       注册服务
     * @param identityVerificationService 实名认证服务
     */
    public PatientController(PatientAccountService patientAccountService,
                             IdentityVerificationService identityVerificationService) {
        this.patientAccountService = patientAccountService;
        this.identityVerificationService = identityVerificationService;
    }

    /**
     * 端点 2：注册并自动登录。
     *
     * @param request        注册请求
     * @param idempotencyKey 幂等键，可为空
     * @param response       用于下发双 Cookie
     * @return 注册结果
     */
    @PostMapping("/patients")
    public Result<PatientRegisterResponse> register(
            @Valid @RequestBody PatientRegisterRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletResponse response) {
        return Result.success(patientAccountService.register(request, idempotencyKey, response));
    }

    /**
     * 端点 8：查询当前患者实名认证状态；身份取自 PatientIdExtractor。
     *
     * @param httpRequest HTTP 请求（Authorization 头）
     * @return 认证状态
     */
    @GetMapping("/patients/current/identity-status")
    public Result<IdentityStatusResponse> identityStatus(HttpServletRequest httpRequest) {
        return Result.success(identityVerificationService.status(httpRequest));
    }
}
