package com.sdas.server.appointment.controller;

import com.sdas.server.appointment.dto.AppointmentCreateRequest;
import com.sdas.server.appointment.dto.AppointmentCreateResponse;
import com.sdas.server.appointment.service.AppointmentService;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 端点 5：创建挂号预约（需认证 + 门禁）。 */
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    private final PatientIdExtractor patientIdExtractor;

    /**
     * 构造挂号预约控制器。
     *
     * @param appointmentService 预约服务
     * @param patientIdExtractor 患者身份解析器
     */
    public AppointmentController(AppointmentService appointmentService,
                                PatientIdExtractor patientIdExtractor) {
        this.appointmentService = appointmentService;
        this.patientIdExtractor = patientIdExtractor;
    }

    /**
     * 创建预约。
     *
     * @param request    创建请求
     * @param httpRequest HTTP 请求
     * @return 预约响应
     */
    @PostMapping
    public Result<AppointmentCreateResponse> create(@Valid @RequestBody AppointmentCreateRequest request,
                                                  HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(appointmentService.create(patientId, request));
    }
}
