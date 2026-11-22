package com.sdas.server.healthconsultation.entity;

import com.sdas.server.healthconsultation.model.EmergencyVerdict;
import java.time.LocalDateTime;

/**
 * 紧急判定留痕实体。
 *
 * <p>表结构来源：specs/005-ai-health-consultation/data-model.md 2.3。
 * <b>禁止写入患者原话、症状描述等病情明文</b>（宪法原则 IV）。</p>
 *
 * @param id           主键
 * @param sessionId    所属会话
 * @param messageId    助手消息主键（落库后回填，可空）
 * @param ruleCode     命中规则码；{@code MODEL_ONLY} 表示仅模型判定
 * @param modelVerdict 模型复核结论
 * @param warned       最终是否预警
 * @param createdAt    创建时间
 */
public record EmergencyDecision(
        long id,
        long sessionId,
        Long messageId,
        String ruleCode,
        EmergencyVerdict modelVerdict,
        boolean warned,
        LocalDateTime createdAt) {
}
