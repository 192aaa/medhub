package com.sdas.server.healthconsultation.model;

/**
 * 消息角色。
 *
 * <p>枚举唯一来源：specs/005-ai-health-consultation/data-model.md 1.1。
 * 未知值降级为 {@link #PATIENT}（保守：不把未知角色当助手消息展示）。</p>
 */
public enum MessageRole {

    /** 患者发言。 */
    PATIENT,

    /** 智能体回答（含 disclaimer 字段，正文不含声明文案）。 */
    ASSISTANT
}
