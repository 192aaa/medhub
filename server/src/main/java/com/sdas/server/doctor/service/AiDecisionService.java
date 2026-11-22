package com.sdas.server.doctor.service;

import com.sdas.server.doctor.dto.AiDecisionResponse;

/**
 * AI 辅助决策服务。
 */
public interface AiDecisionService {

    /**
     * 基于问诊资料与药品目录给出辅助参考，AI 不可用/超时降级为空建议，不阻塞接诊开方。
     *
     * @param doctorId       医生主键
     * @param consultationId 问诊单主键
     * @param query          医生附加提问（可选）
     * @return 决策结果
     */
    AiDecisionResponse decide(long doctorId, long consultationId, String query);
}
