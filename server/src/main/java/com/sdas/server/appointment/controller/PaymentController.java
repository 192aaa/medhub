package com.sdas.server.appointment.controller;

import com.sdas.server.appointment.dto.PaymentRequest;
import com.sdas.server.appointment.dto.PaymentResponse;
import com.sdas.server.appointment.dto.PaymentStatusResponse;
import com.sdas.server.appointment.service.PaymentService;
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

/** 端点 6a/6b：挂号费支付与状态查询（需认证）。 */
@RestController
@RequestMapping("/api/v1/appointments")
public class PaymentController {

    private final PaymentService paymentService;

    private final PatientIdExtractor patientIdExtractor;

    /**
     * 构造支付控制器。
     *
     * @param paymentService     支付服务
     * @param patientIdExtractor 患者身份解析器
     */
    public PaymentController(PaymentService paymentService, PatientIdExtractor patientIdExtractor) {
        this.paymentService = paymentService;
        this.patientIdExtractor = patientIdExtractor;
    }

    /**
     * 支付挂号费。
     *
     * @param appointmentId 挂号单主键
     * @param request       支付请求
     * @param httpRequest   HTTP 请求
     * @return 支付响应
     */
    @PostMapping("/{appointmentId}/payments")
    public Result<PaymentResponse> pay(@PathVariable long appointmentId,
                                      @Valid @RequestBody PaymentRequest request,
                                      HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(paymentService.pay(patientId, appointmentId, request));
    }

    /**
     * 确认支付（沙箱收银台回调接入点）。
     *
     * @param appointmentId 挂号单主键
     * @param paymentId     支付流水主键
     * @param httpRequest   HTTP 请求
     * @return 支付响应
     */
    @PostMapping("/{appointmentId}/payments/{paymentId}/confirm")
    public Result<PaymentResponse> confirm(@PathVariable long appointmentId,
                                          @PathVariable long paymentId,
                                          HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(paymentService.confirm(patientId, appointmentId, paymentId));
    }

    /**
     * 查询支付状态。
     *
     * @param appointmentId 挂号单主键
     * @param httpRequest   HTTP 请求
     * @return 支付状态响应
     */
    @GetMapping("/{appointmentId}/payments")
    public Result<PaymentStatusResponse> query(@PathVariable long appointmentId,
                                              HttpServletRequest httpRequest) {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(paymentService.query(patientId, appointmentId));
    }
}
