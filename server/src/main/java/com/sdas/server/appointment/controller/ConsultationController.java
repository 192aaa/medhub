package com.sdas.server.appointment.controller;

import com.sdas.server.appointment.dto.ConsultationDetailResponse;
import com.sdas.server.appointment.dto.ConsultationSubmitRequest;
import com.sdas.server.appointment.dto.ConsultationSubmitResponse;
import com.sdas.server.appointment.service.ConsultationService;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 端点 8/9：提交图文问诊单与查询详情（需认证）。 */
@RestController
@RequestMapping("/api/v1/consultations")
public class ConsultationController {

    private final ConsultationService consultationService;

    private final PatientIdExtractor patientIdExtractor;

    /**
     * 构造问诊单控制器。
     *
     * @param consultationService 问诊单服务
     * @param patientIdExtractor  患者身份解析器
     */
    public ConsultationController(ConsultationService consultationService,
                                PatientIdExtractor patientIdExtractor) {
        this.consultationService = consultationService;
        this.patientIdExtractor = patientIdExtractor;
    }

    /**
     * 提交问诊单。
     *
     * @param request    提交请求
     * @param httpRequest HTTP 请求
     * @return 提交响应
     */
    @PostMapping
    public Result<ConsultationSubmitResponse> submit(@Valid @RequestBody ConsultationSubmitRequest request,
                                                    HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(consultationService.submit(patientId, request));
    }

    /**
     * 查询问诊单详情。
     *
     * @param consultationId 问诊单主键
     * @param httpRequest    HTTP 请求
     * @return 详情响应
     */
    @GetMapping("/{consultationId}")
    public Result<ConsultationDetailResponse> detail(@PathVariable long consultationId,
                                                   HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(consultationService.detail(patientId, consultationId));
    }
}
