package com.sdas.server.common;

/**
 * 全局业务错误码与提示信息唯一来源。
 *
 * <p>既有取值定义于 specs/health-check/data-model.md 1.2 与 specs/001-patient-identity/data-model.md 1.9；
 * 新增 16 项定义于 specs/002-appointment-consultation/data-model.md 1.9；
 * 003 与 004 的增项分别见 specs/003-doctor-consultation/data-model.md 1.6 与
 * specs/004-prescription-dispensing/data-model.md 1.6；
 * 宪法原则 IV：禁止 Controller/Service 硬编码业务异常 message。</p>
 */
public enum ErrorCode {

    /** 请求成功。 */
    SUCCESS(200, "成功"),
    /** 请求参数错误。 */
    BAD_REQUEST(400, "请求参数错误"),
    /** 未授权。 */
    UNAUTHORIZED(401, "未授权"),
    /** 禁止访问。 */
    FORBIDDEN(403, "禁止访问"),
    /** 资源不存在。 */
    NOT_FOUND(404, "资源不存在"),
    /** 请求方法不支持。 */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    /** 服务端内部错误。 */
    INTERNAL_ERROR(500, "服务端内部错误"),
    /** 健康检查依赖组件不可用。 */
    HEALTH_UNAVAILABLE(503, "依赖组件不可用"),

    /** 手机号格式类失败（specs/001-patient-identity/data-model.md 1.9）。 */
    INVALID_PHONE_FORMAT(400, "手机号须为 11 位中国大陆号码"),
    /** 密码格式类失败。 */
    INVALID_PASSWORD_FORMAT(400, "密码须为 6-20 位字母数字组合，且至少含一个字母和一个数字"),
    /** 验证码格式类失败。 */
    INVALID_VERIFICATION_CODE_FORMAT(400, "验证码须为 6 位数字"),
    /** 姓名格式类失败。 */
    INVALID_NAME_FORMAT(400, "姓名长度须为 1-50 个字符"),
    /** 身份证号格式 / 校验位非法。 */
    INVALID_ID_CARD_FORMAT(400, "身份证号须为 18 位且校验位有效"),
    /** 未勾选协议或协议版本不匹配。 */
    AGREEMENT_NOT_ACCEPTED(400, "请先阅读并同意协议"),
    /** 所有鉴权类失败共用的模糊文案，禁止区分账户存在性。 */
    AUTHENTICATION_FAILED(400, "账号或密码错误，或验证信息无效"),
    /** 未认证账户发起线上问诊（由后续问诊模块消费端点 8 结果后返回）。 */
    IDENTITY_NOT_VERIFIED(403, "请先完成实名认证"),
    /** 双重 Cookie 校验未通过。 */
    CSRF_TOKEN_MISMATCH(403, "请求校验失败"),
    /** 验证码发送 / 实名认证尝试达频次上限。 */
    RATE_LIMITED(429, "操作过于频繁，请稍后重试"),
    /** MySQL / Redis 不可用或超时降级。 */
    SERVICE_UNAVAILABLE(503, "服务暂时不可用，请稍后重试"),

    /** 科室不存在或已停用（specs/002-appointment-consultation/data-model.md 1.9）。 */
    DEPARTMENT_NOT_FOUND(404, "科室不存在或已停用"),
    /** 医生不存在。 */
    DOCTOR_NOT_FOUND(404, "医生不存在"),
    /** 挂号单不存在或不属于当前患者。 */
    APPOINTMENT_NOT_FOUND(404, "挂号单不存在或不属于当前患者"),
    /** 问诊单不存在或不属于当前患者。 */
    CONSULTATION_NOT_FOUND(404, "问诊单不存在或不属于当前患者"),
    /** 主诉长度越界（10-200）。 */
    INVALID_COMPLAINT_FORMAT(400, "主诉长度须为 10-200 个字符"),
    /** 选填描述超长。 */
    INVALID_DESCRIPTION_FORMAT(400, "描述长度超出上限"),
    /** 图片格式不支持。 */
    INVALID_IMAGE_FORMAT(400, "图片格式不支持"),
    /** 单张图片超过 5 MB。 */
    INVALID_IMAGE_SIZE(400, "单张图片不得超过 5 MB"),
    /** 图片数量超过 9 张。 */
    TOO_MANY_IMAGES(400, "图片数量不得超过 9 张"),
    /** 支付金额与应缴金额不一致。 */
    INVALID_PAYMENT_AMOUNT(400, "支付金额与应缴金额不一致"),
    /** 当前挂号单状态不允许该操作。 */
    INVALID_APPOINTMENT_STATUS(400, "当前挂号单状态不允许该操作"),
    /** 所选医生或时段不可预约。 */
    INVALID_SCHEDULE(400, "所选医生或时段不可预约"),
    /** 未认证 / 不具备复诊资格（FR-007 门禁）。 */
    REVISIT_NOT_ELIGIBLE(403, "请先完成实名认证或确认复诊资格"),
    /** 越权访问他人问诊资料（SC-010）。 */
    CONSULTATION_FORBIDDEN(403, "禁止访问他人问诊资料"),
    /** 并发支付竞争（SC-005）。 */
    PAYMENT_CONFLICT(409, "该挂号单已支付，请勿重复支付"),
    /** 模拟支付明确失败（可重试）。 */
    PAYMENT_FAILED(400, "支付失败，请重试"),

    /** 非医生角色访问医生工作台（specs/003-doctor-consultation/data-model.md 1.6）。 */
    DOCTOR_ROLE_REQUIRED(403, "仅医生可访问该功能"),
    /** 医生账号不存在或已禁用。 */
    DOCTOR_ACCOUNT_NOT_FOUND(404, "医生账号不存在或已禁用"),
    /** 回复为空 / 超 1000 字 / 含控制字符。 */
    INVALID_REPLY_FORMAT(400, "回复内容须为 1-1000 字符"),
    /** 状态不允许当前操作（未接诊即回复/开方、已完成单再操作）。 */
    INVALID_CONSULTATION_STATUS(400, "当前问诊单状态不允许该操作"),
    /** 并发/重复接诊（仅一人成功）。 */
    CONSULTATION_ACCEPT_CONFLICT(409, "该问诊单已被其他医生接诊"),
    /** 药品不存在或已停用。 */
    DRUG_NOT_FOUND(404, "药品不存在或已停用"),
    /** 自由输入药品名 / 不在目录。 */
    DRUG_NOT_IN_CATALOG(400, "请从药品目录中选择药品"),
    /** 处方字段（规格/用法用量/注意事项）为空或超长。 */
    INVALID_PRESCRIPTION_FORMAT(400, "处方信息不完整或超出限制"),
    /** 一张问诊单重复开具处方。 */
    PRESCRIPTION_ALREADY_EXISTS(409, "该问诊单已开具处方"),
    /** 处方不存在或越权访问（message 逐字取自 004 data-model.md 1.6）。 */
    PRESCRIPTION_NOT_FOUND(404, "处方不存在或不属于当前患者"),
    /** 签名密钥缺失或签名失败。 */
    PRESCRIPTION_SIGN_FAILED(400, "处方签名失败，请重试"),
    /** 处方已过期。 */
    PRESCRIPTION_EXPIRED(400, "处方已过期"),

    /* ---- 004 处方取药：specs/004-prescription-dispensing/data-model.md 1.6 ---- */

    /** 非药店角色访问药店端点。 */
    PHARMACY_ROLE_REQUIRED(403, "仅药店工作人员可访问该功能"),
    /** 药店账号不存在或已禁用。 */
    PHARMACY_ACCOUNT_NOT_FOUND(404, "药店账号不存在或已禁用"),
    /** 令牌结构 / 签名 / 解密 / 令牌有效期 / 验真码失败。 */
    INVALID_VERIFICATION_PROOF(400, "验真凭证无效，请重新扫码"),
    /** 处方处于草稿态（未签名），不可验真通过或核销。 */
    PRESCRIPTION_NOT_SIGNED(400, "处方未签名，无法取药"),
    /** 处方已作废，不可核销。 */
    PRESCRIPTION_REVOKED(400, "处方已作废，无法取药"),

    /* ---- 005 AI健康咨询：specs/005-ai-health-consultation/data-model.md 第 5 节 ---- */

    /** 会话不存在 / 不属于当前患者 / 已删除（越权与不存在同码）。 */
    SESSION_NOT_FOUND(404, "会话不存在"),
    /** 会话标题为空或超过 64 字符。 */
    SESSION_TITLE_INVALID(400, "会话标题不合法"),
    /** 患者单条输入超过上限。 */
    MESSAGE_TOO_LONG(400, "消息内容过长"),
    /** 单会话消息数已达上限。 */
    SESSION_MESSAGE_LIMIT(400, "当前会话消息数已达上限"),
    /** 同会话已有生成中的回答。 */
    CHAT_BUSY(409, "当前会话正在生成回答，请稍候"),
    /** 模型不可用且无法给出任何内容。 */
    AI_SERVICE_UNAVAILABLE(503, "智能助手暂时不可用，请稍后重试"),

    /* ---- 006 AI辅助治疗决策：specs/006-ai-treatment-decision/data-model.md 第 5 节 ---- */

    /** 该问诊单暂无任何辅助记录（端点 2）。 */
    TREATMENT_DECISION_NOT_FOUND(404, "暂无治疗决策辅助记录"),
    /** 并发生成未获得锁。 */
    TREATMENT_DECISION_BUSY(409, "辅助生成进行中，请稍候"),
    /** 重新生成超过每小时上限。 */
    TREATMENT_DECISION_RATE_LIMITED(429, "辅助生成过于频繁，请稍后重试"),
    /** 采纳数量缺省或超出 0–20。 */
    INVALID_TREATMENT_ADOPTION(400, "采纳数量不合法");

    private final int code;

    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /** @return 业务状态码，与 HTTP 状态保持一致 */
    public int getCode() {
        return code;
    }

    /** @return 统一提示信息 */
    public String getMessage() {
        return message;
    }
}
