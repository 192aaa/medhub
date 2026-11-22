package com.sdas.server.doctor.controller;

import com.sdas.server.common.DoctorIdExtractor;
import com.sdas.server.common.Result;
import com.sdas.server.doctor.dto.PrescriptionCreateRequest;
import com.sdas.server.doctor.dto.PrescriptionDetailResponse;
import com.sdas.server.doctor.dto.PrescriptionResponse;
import com.sdas.server.doctor.service.PrescriptionService;
import com.sdas.server.identity.service.DoctorTokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医生处方端点 7/8：开具电子处方、查询处方详情。
 */
@RestController
@RequestMapping("/api/v1/workbench")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    private final DoctorIdExtractor doctorIdExtractor;

    /**
     * 构造处方控制器。
     *
     * @param prescriptionService 处方服务
     * @param doctorIdExtractor   医生身份提取器
     */
    public PrescriptionController(PrescriptionService prescriptionService,
                                  DoctorIdExtractor doctorIdExtractor) {
        this.prescriptionService = prescriptionService;
        this.doctorIdExtractor = doctorIdExtractor;
    }

    /**
     * 端点 7：开具电子处方。
     *
     * @param consultationId 问诊单主键
     * @param request        处方请求
     * @param httpRequest    HTTP 请求
     * @return 处方结果
     */
    @PostMapping("/consultations/{consultationId}/prescriptions")
    public Result<PrescriptionResponse> create(@PathVariable long consultationId,
                                               @Valid @RequestBody PrescriptionCreateRequest request,
                                               HttpServletRequest httpRequest) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(httpRequest);
        return Result.success(prescriptionService.create(claims.doctorId(), consultationId, request));
    }

    /**
     * 端点 8：查询处方详情。
     *
     * @param prescriptionId 处方主键
     * @param httpRequest    HTTP 请求
     * @return 处方详情
     */
    @GetMapping("/prescriptions/{prescriptionId}")
    public Result<PrescriptionDetailResponse> detail(@PathVariable long prescriptionId,
                                                      HttpServletRequest httpRequest) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(httpRequest);
        return Result.success(prescriptionService.detail(claims.doctorId(), prescriptionId));
    }
}
