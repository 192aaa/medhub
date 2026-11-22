package com.sdas.server.doctor.service;

import com.sdas.server.doctor.dto.TreatmentAdoptionResponse;
import com.sdas.server.doctor.dto.TreatmentDecisionResponse;
import com.sdas.server.doctor.model.TreatmentTriggerType;

/**
 * 治疗决策辅助服务（端点 1–4，plan.md 时序第 4–13 步）。
 *
 * <p>全流程只读问诊资料与知识库；AI 输出不写处方表，处方仍走 003 既有签名链路。</p>
 */
public interface TreatmentDecisionService {

    /**
     * 生成或复用治疗决策辅助。
     *
     * @param doctorId       请求医生
     * @param consultationId 问诊单主键
     * @param trigger        触发方式（{@code AUTO} 允许幂等复用）
     * @return 辅助结果（失败为降级结构，HTTP 仍 200）
     */
    TreatmentDecisionResponse generate(long doctorId, long consultationId,
                                       TreatmentTriggerType trigger);

    /**
     * 查询该问诊单本医生最近一条辅助记录。
     *
     * @param doctorId       请求医生
     * @param consultationId 问诊单主键
     * @return 最近一次结果
     */
    TreatmentDecisionResponse latest(long doctorId, long consultationId);

    /**
     * 回填采纳数量（不影响处方提交）。
     *
     * @param doctorId        请求医生
     * @param consultationId  问诊单主键
     * @param appliedItemCount 采纳条数（0–20）
     * @return 回填结果
     */
    TreatmentAdoptionResponse markAdopted(long doctorId, long consultationId,
                                          Integer appliedItemCount);
}
