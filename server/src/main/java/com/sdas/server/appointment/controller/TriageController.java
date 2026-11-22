package com.sdas.server.appointment.controller;

import com.sdas.server.appointment.dto.TriageRequest;
import com.sdas.server.appointment.dto.TriageResponse;
import com.sdas.server.appointment.service.TriageService;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 端点 4：AI 智能导诊（允许匿名，CSRF 必带）。 */
@RestController
@RequestMapping("/api/v1/triage-sessions")
public class TriageController {

    private final TriageService triageService;

    private final PatientIdExtractor patientIdExtractor;

    /**
     * 构造导诊控制器。
     *
     * @param triageService      导诊服务
     * @param patientIdExtractor 患者身份解析器
     */
    public TriageController(TriageService triageService, PatientIdExtractor patientIdExtractor) {
        this.triageService = triageService;
        this.patientIdExtractor = patientIdExtractor;
    }

    /**
     * 发起导诊；匿名访问时 patientId 为空。
     *
     * @param request 导诊请求
     * @param httpRequest HTTP 请求（用于尝试解析身份）
     * @return 导诊响应（含降级分支）
     */
    @PostMapping
    public Result<TriageResponse> triage(@Valid @RequestBody TriageRequest request,
                                        HttpServletRequest httpRequest) {
        Long patientId = tryExtractPatientId(httpRequest);
        return Result.success(triageService.triage(patientId, request.complaint()));
    }

    private Long tryExtractPatientId(HttpServletRequest httpRequest) {
        try {
            return patientIdExtractor.extract(httpRequest).patientId();
        } catch (BusinessException exception) {
            return null;
        }
    }
}
