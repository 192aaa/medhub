package com.sdas.server.identity.model;

/**
 * 安全审计事件类型。
 *
 * <p>唯一来源：specs/001-patient-identity/data-model.md 1.8（前 10 项）、
 * specs/002-appointment-consultation/data-model.md 1.10（其后 9 项）、
 * specs/003-doctor-consultation/data-model.md（医生接诊 7 项）、
 * specs/004-prescription-dispensing/tasks.md T009（处方取药 7 项）。
 * 全部事件写入 security_audit_event，日志与响应禁止出现完整手机号/身份证号/支付敏感信息。</p>
 */
public enum AuditEventType {
    /** 验证码签发。 */
    VERIFICATION_CODE_SENT,
    /** 注册成功。 */
    PATIENT_REGISTERED,
    /** 登录成功。 */
    PATIENT_LOGIN_SUCCEEDED,
    /** 登录失败（含频次限制触发）。 */
    PATIENT_LOGIN_FAILED,
    /** 安全退出。 */
    PATIENT_LOGGED_OUT,
    /** 重置密码成功。 */
    PASSWORD_RESET,
    /** 实名认证通过。 */
    IDENTITY_VERIFIED,
    /** 实名认证未通过。 */
    IDENTITY_REJECTED,
    /** 续期凭证轮换。 */
    SESSION_REFRESHED,
    /** 未认证访问问诊被拒。 */
    CONSULTATION_GATE_DENIED,

    /** 挂号单创建（002）。 */
    APPOINTMENT_CREATED,
    /** 挂号费支付成功（002）。 */
    APPOINTMENT_PAID,
    /** 支付失败（002）。 */
    PAYMENT_FAILED,
    /** 图文问诊单提交（002）。 */
    CONSULTATION_SUBMITTED,
    /** 问诊图片上传（002）。 */
    IMAGE_UPLOADED,
    /** AI 智能导诊推荐成功（002）。 */
    TRIAGE_RECOMMENDED,
    /** AI 导诊超时降级为全量科室列表（002，HTTP 200）。 */
    TRIAGE_TIMEOUT_FALLBACK,
    /** 复诊资格门禁拒绝（002）。 */
    REVISIT_GATE_DENIED,

    /** 医生登录（003）。 */
    DOCTOR_LOGIN,
    /** 接诊（003）。 */
    CONSULTATION_ACCEPTED,
    /** 医生回复（003）。 */
    CONSULTATION_REPLIED,
    /** 开具处方（签名成功，003）。 */
    PRESCRIPTION_CREATED,
    /** 处方签名失败（003）。 */
    PRESCRIPTION_SIGN_FAILED,
    /** 接诊超时兜底（003）。 */
    CONSULTATION_TIMEOUT,
    /** AI 辅助决策调用（003）。 */
    AI_DECISION_REQUESTED,

    /* ---- 004 处方取药：specs/004-prescription-dispensing/tasks.md T009 ---- */

    /** 药店登录成功。 */
    PHARMACY_LOGIN_SUCCESS,
    /** 药店登录失败（账号缺失 / 禁用 / 口令错误，文案模糊）。 */
    PHARMACY_LOGIN_FAILED,
    /** 处方验真成功。 */
    PRESCRIPTION_VERIFICATION_SUCCESS,
    /** 处方验真失败（令牌无效 / 草稿 / 处方不存在等）。 */
    PRESCRIPTION_VERIFICATION_FAILED,
    /** 处方核销成功（首次）。 */
    PRESCRIPTION_DISPENSED,
    /** 重复核销（幂等命中已有记录）。 */
    PRESCRIPTION_DISPENSE_REPEATED,
    /** 核销失败（过期 / 作废 / 草稿 / 凭证无效 / 其他写入失败）。 */
    PRESCRIPTION_DISPENSE_FAILED,

    /** 越权访问他人 AI 咨询会话（005，FR-011/FR-014）。 */
    AI_HEALTH_ACCESS_DENIED,
    /** 患者清空全部 AI 咨询历史（005，FR-017，只记数量）。 */
    AI_HEALTH_HISTORY_CLEARED,
    /** AI 回答生成失败或超时（005，FR-012）。 */
    AI_HEALTH_CHAT_FAILED,

    /** 治疗决策辅助请求（006：成功与降级都记，result 区分）。 */
    AI_TREATMENT_DECISION_REQUESTED,
    /** 治疗决策辅助降级（006：reasonCode 记 DegradedReason）。 */
    AI_TREATMENT_DECISION_DEGRADED,
    /** 医生采纳治疗决策辅助内容（006：只记数量）。 */
    AI_TREATMENT_DECISION_ADOPTED
}
