package com.sdas.server.doctor.controller;

import com.sdas.server.common.DoctorIdExtractor;
import com.sdas.server.common.Result;
import com.sdas.server.doctor.dto.AiDecisionRequest;
import com.sdas.server.doctor.dto.AiDecisionResponse;
import com.sdas.server.doctor.service.AiDecisionService;
import com.sdas.server.identity.service.DoctorTokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医生 AI 辅助决策端点 9。
 */
@RestController
@RequestMapping("/api/v1/workbench")
public class AiDecisionController {

    private final AiDecisionService aiDecisionService;

    private final DoctorIdExtractor doctorIdExtractor;

    /**
     * 构造 AI 决策控制器。
     *
     * @param aiDecisionService AI 决策服务
     * @param doctorIdExtractor 医生身份提取器
     */
    public AiDecisionController(AiDecisionService aiDecisionService,
                                DoctorIdExtractor doctorIdExtractor) {
        this.aiDecisionService = aiDecisionService;
        this.doctorIdExtractor = doctorIdExtractor;
    }

    /**
     * 端点 9：AI 辅助决策。
     *
     * @param consultationId 问诊单主键
     * @param request        决策请求
     * @param httpRequest    HTTP 请求
     * @return 决策结果
     */
    @PostMapping("/consultations/{consultationId}/ai-decision")
    public Result<AiDecisionResponse> decide(@PathVariable long consultationId,
                                             @Valid @RequestBody(required = false) AiDecisionRequest request,
                                             HttpServletRequest httpRequest) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(httpRequest);
        String query = request == null ? null : request.query();
        return Result.success(aiDecisionService.decide(claims.doctorId(), consultationId, query));
    }
}
