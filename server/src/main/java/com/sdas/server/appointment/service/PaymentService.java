package com.sdas.server.appointment.service;

import com.sdas.server.appointment.dto.PaymentRequest;
import com.sdas.server.appointment.dto.PaymentResponse;
import com.sdas.server.appointment.dto.PaymentStatusResponse;

/** 挂号费支付服务（端点 6a/6b，MVP 模拟支付）。 */
public interface PaymentService {

    /**
     * 支付挂号费（幂等、同事务更新预约状态）。
     *
     * @param patientId     当前患者
     * @param appointmentId 挂号单
     * @param request       支付请求
     * @return 支付响应
     */
    PaymentResponse pay(long patientId, long appointmentId, PaymentRequest request);

    /**
     * 查询支付状态。
     *
     * @param patientId     当前患者
     * @param appointmentId 挂号单
     * @return 支付状态响应
     */
    PaymentStatusResponse query(long patientId, long appointmentId);
}
