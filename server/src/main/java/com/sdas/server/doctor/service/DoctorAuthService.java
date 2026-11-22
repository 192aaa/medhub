package com.sdas.server.doctor.service;

import com.sdas.server.doctor.dto.LoginRequest;
import com.sdas.server.doctor.dto.LoginResponse;

/**
 * 医生登录服务（复用 001 统一认证体系，新增「医生」角色）。
 */
public interface DoctorAuthService {

    /**
     * 医生账号密码登录，签发含「医生」角色的 access token。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    LoginResponse login(LoginRequest request);
}
