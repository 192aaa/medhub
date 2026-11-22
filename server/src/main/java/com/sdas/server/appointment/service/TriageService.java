package com.sdas.server.appointment.service;

import com.sdas.server.appointment.dto.TriageResponse;

/** AI 智能导诊服务（端点 4）。 */
public interface TriageService {

    /**
     * 根据症状推荐科室；AI 超时/异常降级为全量科室列表（HTTP 200，SC-006）。
     *
     * @param patientId 当前患者（匿名时为空）
     * @param complaint 症状描述
     * @return 导诊响应
     */
    TriageResponse triage(Long patientId, String complaint);
}
