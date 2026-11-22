package com.sdas.server.pharmacy.service;

import com.sdas.server.pharmacy.dto.VerificationRequest;
import com.sdas.server.pharmacy.dto.VerificationResponse;

/**
 * 处方验真（端点 2）。
 *
 * <p>只读且可重复调用：不消费令牌、不改变任何状态。过期与作废均以
 * {@code 200 + dispenseStatus + dispensable=false} 表达；仅 {@code DRAFT} 抛
 * {@code PRESCRIPTION_NOT_SIGNED}。</p>
 */
public interface PrescriptionVerificationService {

    /**
     * 校验凭证并返回处方展示信息。
     *
     * @param request 验真请求（扫码或手工输入二选一）
     * @return 验真结果
     */
    VerificationResponse verify(VerificationRequest request);
}
