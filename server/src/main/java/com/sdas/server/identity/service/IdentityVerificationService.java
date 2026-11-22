package com.sdas.server.identity.service;

import com.sdas.server.identity.dto.IdentityStatusResponse;
import com.sdas.server.identity.dto.IdentityVerificationRequest;
import com.sdas.server.identity.dto.IdentityVerificationResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 实名认证与认证状态查询（FR-011 ~ FR-016、FR-019、FR-022）。
 *
 * <p>身份统一由 PatientIdExtractor 从 JWT 提取；本服务不产出
 * IDENTITY_NOT_VERIFIED(403)，门禁由后续问诊模块消费端点 8 结果后判定。</p>
 */
public interface IdentityVerificationService {

    /**
     * 提交实名认证；未命中 / 已被占用一律返回 AUTHENTICATION_FAILED 统一文案。
     *
     * @param request HTTP 请求（Authorization 头）
     * @param body    认证请求
     * @return 认证成功结果
     */
    IdentityVerificationResponse verify(HttpServletRequest request,
                                        IdentityVerificationRequest body);

    /**
     * 查询当前患者认证状态；consultationAllowed 与 authStatus 严格联动。
     *
     * @param request HTTP 请求（Authorization 头）
     * @return 认证状态
     */
    IdentityStatusResponse status(HttpServletRequest request);
}
