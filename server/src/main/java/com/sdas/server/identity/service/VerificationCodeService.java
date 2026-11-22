package com.sdas.server.identity.service;

import com.sdas.server.identity.dto.VerificationCodeSendRequest;
import com.sdas.server.identity.dto.VerificationCodeSendResponse;
import com.sdas.server.identity.model.VerificationPurpose;

/**
 * 验证码签发与消费编排（D1：固定验证码仅 dev/test，不发送短信）。
 */
public interface VerificationCodeService {

    /**
     * 签发验证码，响应与日志均不输出明文。
     *
     * @param request 签发请求
     * @return 回显用途 / 脱敏手机号 / TTL / 发送间隔
     */
    VerificationCodeSendResponse send(VerificationCodeSendRequest request);

    /**
     * 校验并原子消费验证码；错误 / 过期 / 重放 / 跨用途统一失败。
     *
     * @param purpose 用途
     * @param phone   明文手机号
     * @param code    验证码
     */
    void consume(VerificationPurpose purpose, String phone, String code);

    /**
     * 清理指定用途的验证码（重置密码成功后调用）。
     *
     * @param purpose 用途
     * @param phone   明文手机号
     */
    void clear(VerificationPurpose purpose, String phone);
}
