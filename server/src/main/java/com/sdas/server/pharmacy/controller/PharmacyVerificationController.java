package com.sdas.server.pharmacy.controller;

import com.sdas.server.common.PharmacyIdExtractor;
import com.sdas.server.common.Result;
import com.sdas.server.pharmacy.dto.VerificationRequest;
import com.sdas.server.pharmacy.dto.VerificationResponse;
import com.sdas.server.pharmacy.service.PrescriptionVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 处方验真端点（004 端点 2）：药店角色 + CSRF；只读、可重复调用。 */
@RestController
@RequestMapping("/api/v1/pharmacy/prescription-verifications")
public class PharmacyVerificationController {

    private final PharmacyIdExtractor pharmacyIdExtractor;

    private final PrescriptionVerificationService verificationService;

    /**
     * 构造控制器。
     *
     * @param pharmacyIdExtractor   药店身份提取器（统一角色校验）
     * @param verificationService   验真服务
     */
    public PharmacyVerificationController(PharmacyIdExtractor pharmacyIdExtractor,
                                          PrescriptionVerificationService verificationService) {
        this.pharmacyIdExtractor = pharmacyIdExtractor;
        this.verificationService = verificationService;
    }

    /**
     * 处方验真。
     *
     * @param httpRequest HTTP 请求（提取药店身份）
     * @param request     验真请求
     * @return 验真结果
     */
    @PostMapping
    public Result<VerificationResponse> verify(HttpServletRequest httpRequest,
                                               @Valid @RequestBody VerificationRequest request) {
        pharmacyIdExtractor.extract(httpRequest);
        return Result.success(verificationService.verify(request));
    }
}
