package com.sdas.server.doctor.controller;

import com.sdas.server.common.DoctorIdExtractor;
import com.sdas.server.common.Result;
import com.sdas.server.doctor.dto.TreatmentAdoptionRequest;
import com.sdas.server.doctor.dto.TreatmentAdoptionResponse;
import com.sdas.server.doctor.dto.TreatmentDecisionResponse;
import com.sdas.server.doctor.model.TreatmentTriggerType;
import com.sdas.server.doctor.service.TreatmentDecisionService;
import com.sdas.server.identity.service.DoctorTokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 治疗决策辅助端点 1–4（contracts/ai-treatment-decision-api.md）。
 *
 * <p>医生身份统一由 {@link DoctorIdExtractor} 提取，禁止 Controller 解析令牌；
 * AI 失败不返回 5xx，降级信息在响应 {@code data.status} 中表达。</p>
 */
@RestController
@RequestMapping("/api/v1/workbench/consultations/{consultationId}/treatment-decision")
public class TreatmentDecisionController {

    private final TreatmentDecisionService treatmentDecisionService;

    private final DoctorIdExtractor doctorIdExtractor;

    /**
     * 构造控制器。
     *
     * @param treatmentDecisionService 治疗决策服务
     * @param doctorIdExtractor        医生身份提取器
     */
    public TreatmentDecisionController(TreatmentDecisionService treatmentDecisionService,
                                       DoctorIdExtractor doctorIdExtractor) {
        this.treatmentDecisionService = treatmentDecisionService;
        this.doctorIdExtractor = doctorIdExtractor;
    }

    /**
     * 端点 1：生成或复用治疗决策辅助。
     *
     * @param consultationId 问诊单主键
     * @param request        HTTP 请求
     * @return 辅助结果
     */
    @PostMapping
    public Result<TreatmentDecisionResponse> generate(@PathVariable long consultationId,
                                                      HttpServletRequest request) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(request);
        return Result.success(treatmentDecisionService.generate(claims.doctorId(),
                consultationId, TreatmentTriggerType.AUTO));
    }

    /**
     * 端点 2：查询最近一次辅助记录。
     *
     * @param consultationId 问诊单主键
     * @param request        HTTP 请求
     * @return 最近一次结果
     */
    @GetMapping
    public Result<TreatmentDecisionResponse> latest(@PathVariable long consultationId,
                                                    HttpServletRequest request) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(request);
        return Result.success(treatmentDecisionService.latest(claims.doctorId(), consultationId));
    }

    /**
     * 端点 3：强制重新生成（跳过幂等复用，消耗频控额度）。
     *
     * @param consultationId 问诊单主键
     * @param request        HTTP 请求
     * @return 辅助结果
     */
    @PostMapping("/regenerate")
    public Result<TreatmentDecisionResponse> regenerate(@PathVariable long consultationId,
                                                        HttpServletRequest request) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(request);
        return Result.success(treatmentDecisionService.generate(claims.doctorId(),
                consultationId, TreatmentTriggerType.REGENERATE));
    }

    /**
     * 端点 4：回填采纳数量（失败不阻断开方）。
     *
     * @param consultationId 问诊单主键
     * @param body           请求体（可空，缺省视为非法）
     * @param request        HTTP 请求
     * @return 回填结果
     */
    @PostMapping("/adoptions")
    public Result<TreatmentAdoptionResponse> adopt(
            @PathVariable long consultationId,
            @RequestBody(required = false) TreatmentAdoptionRequest body,
            HttpServletRequest request) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(request);
        Integer appliedItemCount = body == null ? null : body.appliedItemCount();
        return Result.success(treatmentDecisionService.markAdopted(claims.doctorId(),
                consultationId, appliedItemCount));
    }
}
