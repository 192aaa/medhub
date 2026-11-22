package com.sdas.server.identity.service;

import com.sdas.server.identity.dto.PatientRegisterRequest;
import com.sdas.server.identity.dto.PatientRegisterResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 患者注册（自动登录）事务编排。
 */
public interface PatientAccountService {

    /**
     * 注册并自动登录；失败整体回滚，不产生半成品账户。
     *
     * @param request        注册请求
     * @param idempotencyKey 幂等键，可为空
     * @param response       用于下发 refresh / CSRF Cookie
     * @return 注册结果（含 access token）
     */
    PatientRegisterResponse register(PatientRegisterRequest request, String idempotencyKey,
                                     HttpServletResponse response);
}
