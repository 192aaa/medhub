package com.sdas.server.appointment.service;

import com.sdas.server.appointment.dto.ConsultationDetailResponse;
import com.sdas.server.appointment.dto.ConsultationSubmitRequest;
import com.sdas.server.appointment.dto.ConsultationSubmitResponse;

/** 图文问诊单服务（端点 8/9）。 */
public interface ConsultationService {

    /**
     * 提交图文问诊单（待接诊，AI 摘要随单生成）。
     *
     * @param patientId 当前患者
     * @param request   提交请求
     * @return 提交响应
     */
    ConsultationSubmitResponse submit(long patientId, ConsultationSubmitRequest request);

    /**
     * 查询问诊单详情（越权拒绝）。
     *
     * @param patientId       当前患者
     * @param consultationId  问诊单
     * @return 详情响应
     */
    ConsultationDetailResponse detail(long patientId, long consultationId);
}
