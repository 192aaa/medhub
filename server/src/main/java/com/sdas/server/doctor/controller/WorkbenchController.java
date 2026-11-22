package com.sdas.server.doctor.controller;

import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.DoctorIdExtractor;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.common.Result;
import com.sdas.server.doctor.dto.AcceptResponse;
import com.sdas.server.doctor.dto.ConsultationDetailResponse;
import com.sdas.server.doctor.dto.ConsultationListItem;
import com.sdas.server.doctor.dto.LoginRequest;
import com.sdas.server.doctor.dto.LoginResponse;
import com.sdas.server.doctor.dto.MessageItem;
import com.sdas.server.doctor.dto.MessageRequest;
import com.sdas.server.doctor.dto.MessageResponse;
import com.sdas.server.doctor.service.DoctorAuthService;
import com.sdas.server.doctor.service.WorkbenchService;
import com.sdas.server.identity.service.DoctorTokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医生工作台端点 1/2/3：登录、待接诊/已接诊列表、问诊单详情。
 */
@RestController
@RequestMapping("/api/v1/workbench")
public class WorkbenchController {

    private final DoctorAuthService doctorAuthService;

    private final WorkbenchService workbenchService;

    private final DoctorIdExtractor doctorIdExtractor;

    /**
     * 构造工作台控制器。
     *
     * @param doctorAuthService 医生登录服务
     * @param workbenchService  工作台服务
     * @param doctorIdExtractor 医生身份提取器
     */
    public WorkbenchController(DoctorAuthService doctorAuthService,
                               WorkbenchService workbenchService,
                               DoctorIdExtractor doctorIdExtractor) {
        this.doctorAuthService = doctorAuthService;
        this.workbenchService = workbenchService;
        this.doctorIdExtractor = doctorIdExtractor;
    }

    /**
     * 端点 1：医生登录。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @PostMapping("/sessions")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(doctorAuthService.login(request));
    }

    /**
     * 端点 2：查询待接诊/已接诊列表。
     *
     * @param status  问诊单状态（PENDING 默认 / IN_PROGRESS）
     * @param request HTTP 请求
     * @return 问诊单列表
     */
    @GetMapping("/consultations")
    public Result<List<ConsultationListItem>> list(
            @RequestParam(required = false) String status, HttpServletRequest request) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(request);
        return Result.success(workbenchService.list(claims.doctorId(), parseStatus(status)));
    }

    /**
     * 端点 3：查询问诊单详情。
     *
     * @param consultationId 问诊单主键
     * @param request        HTTP 请求
     * @return 问诊单详情
     */
    @GetMapping("/consultations/{consultationId}")
    public Result<ConsultationDetailResponse> detail(@PathVariable long consultationId,
                                                      HttpServletRequest request) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(request);
        return Result.success(workbenchService.detail(claims.doctorId(), consultationId));
    }

    /**
     * 端点 4：接诊。
     *
     * @param consultationId 问诊单主键
     * @param request        HTTP 请求
     * @return 接诊结果
     */
    @PostMapping("/consultations/{consultationId}/accept")
    public Result<AcceptResponse> accept(@PathVariable long consultationId,
                                         HttpServletRequest request) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(request);
        return Result.success(workbenchService.accept(claims.doctorId(), consultationId));
    }

    /**
     * 端点 5：查询对话消息。
     *
     * @param consultationId 问诊单主键
     * @param request        HTTP 请求
     * @return 消息列表
     */
    @GetMapping("/consultations/{consultationId}/messages")
    public Result<List<MessageItem>> listMessages(@PathVariable long consultationId,
                                                   HttpServletRequest request) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(request);
        return Result.success(workbenchService.listMessages(claims.doctorId(), consultationId));
    }

    /**
     * 端点 6：发送回复。
     *
     * @param consultationId 问诊单主键
     * @param messageRequest 回复请求
     * @param request        HTTP 请求
     * @return 回复回显
     */
    @PostMapping("/consultations/{consultationId}/messages")
    public Result<MessageResponse> reply(@PathVariable long consultationId,
                                         @Valid @RequestBody MessageRequest messageRequest,
                                         HttpServletRequest request) {
        DoctorTokenClaims claims = doctorIdExtractor.extract(request);
        return Result.success(workbenchService.reply(claims.doctorId(), consultationId,
                messageRequest.content()));
    }

    private ConsultationStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "PENDING".equalsIgnoreCase(status)) {
            return ConsultationStatus.PENDING;
        }
        if ("IN_PROGRESS".equalsIgnoreCase(status)) {
            return ConsultationStatus.IN_PROGRESS;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
}
