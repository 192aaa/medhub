package com.sdas.server.healthconsultation.model;

/**
 * 咨询会话状态。
 *
 * <p>枚举唯一来源：specs/005-ai-health-consultation/data-model.md 1.2。
 * 不设 ARCHIVED：本期无归档需求。</p>
 */
public enum SessionStatus {

    /** 正常会话。 */
    ACTIVE,

    /** 患者已删除（逻辑删除，查询一律过滤）。 */
    DELETED
}
