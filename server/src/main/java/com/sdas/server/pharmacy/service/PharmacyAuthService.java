package com.sdas.server.pharmacy.service;

import com.sdas.server.pharmacy.dto.PharmacyLoginRequest;
import com.sdas.server.pharmacy.dto.PharmacyLoginResponse;

/**
 * 药店登录（端点 1）。
 *
 * <p>失败原因（账号不存在 / 口令错误 / 账号禁用）对外统一为
 * {@code AUTHENTICATION_FAILED}，不区分，避免账号枚举。</p>
 */
public interface PharmacyAuthService {

    /**
     * 校验口令并签发药店访问令牌。
     *
     * @param request 登录请求
     * @return 登录响应（含 accessToken）
     */
    PharmacyLoginResponse login(PharmacyLoginRequest request);
}
