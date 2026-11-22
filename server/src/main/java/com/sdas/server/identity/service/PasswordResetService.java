package com.sdas.server.identity.service;

import com.sdas.server.identity.dto.PasswordResetRequest;

/**
 * 重置密码（端点 6）：验证码消费 → 更新密码哈希 → 撤销全部会话 → 清理验证码 → 审计。
 */
public interface PasswordResetService {

    /**
     * 重置指定手机号的登录密码。
     *
     * @param request 重置请求
     */
    void reset(PasswordResetRequest request);
}
