package com.sdas.server.healthconsultation.service;

import com.sdas.server.healthconsultation.support.EmergencyDecisionResolver;
import com.sdas.server.healthconsultation.support.EmergencyHeaderParser.ModelEmergencySignal;
import com.sdas.server.healthconsultation.dto.HealthSseEvents;

/**
 * 紧急症状判定（规则优先 + 模型复核，R-07）。
 */
public interface EmergencySymptomService {

    /**
     * 评估本轮是否需要预警并写判定留痕（不含病情明文）。
     *
     * @param sessionId   会话主键
     * @param patientText 患者本轮输入（仅用于规则匹配，不落库到判定表）
     * @param signal      模型首行信号
     * @return 评估结果
     */
    Evaluation evaluate(long sessionId, String patientText, ModelEmergencySignal signal);

    /**
     * 助手消息落库后回填判定记录。
     *
     * @param decisionId 判定记录主键（可为 null）
     * @param messageId  助手消息主键
     */
    void attachMessage(Long decisionId, long messageId);

    /**
     * 评估结果。
     *
     * @param decisionId 留痕主键（未产生记录时为 null）
     * @param result     判定结论（未产生记录时为 null）
     * @param payload    需要预警时的 SSE 载荷（不预警时为 null）
     */
    record Evaluation(Long decisionId, EmergencyDecisionResolver.Result result,
                      HealthSseEvents.Emergency payload) {

        /**
         * 是否应发送 emergency 事件。
         *
         * @return true 表示需要预警
         */
        public boolean shouldWarn() {
            return payload != null;
        }
    }
}
