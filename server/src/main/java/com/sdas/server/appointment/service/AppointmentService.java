package com.sdas.server.appointment.service;

import com.sdas.server.appointment.dto.AppointmentCreateRequest;
import com.sdas.server.appointment.dto.AppointmentCreateResponse;

/** 挂号预约服务（端点 5）。 */
public interface AppointmentService {

    /**
     * 创建挂号预约（含复诊门禁与排班校验）。
     *
     * @param patientId 当前患者
     * @param request   创建请求
     * @return 预约响应
     */
    AppointmentCreateResponse create(long patientId, AppointmentCreateRequest request);
}
