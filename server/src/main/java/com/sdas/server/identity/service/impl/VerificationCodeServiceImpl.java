package com.sdas.server.identity.service.impl;

import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.dto.VerificationCodeSendRequest;
import com.sdas.server.identity.dto.VerificationCodeSendResponse;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.model.VerificationPurpose;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.MaskService;
import com.sdas.server.identity.service.RateLimitService;
import com.sdas.server.identity.service.VerificationCodeService;
import com.sdas.server.identity.support.VerificationCodeStore;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;

/**
 * 验证码签发 / 消费编排：Redis 只存 HMAC 摘要，日志与响应不输出明文。
 */
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityProperties properties;

    private final VerificationCodeStore store;

    private final RateLimitService rateLimitService;

    private final CryptoService cryptoService;

    private final MaskService maskService;

    private final AuditEventService auditEventService;

    /**
     * 构造验证码服务。
     *
     * @param properties        患者身份模块配置
     * @param store             验证码 Redis 存储
     * @param rateLimitService  频次限制
     * @param cryptoService     摘要工具
     * @param maskService       脱敏工具
     * @param auditEventService 审计服务
     */
    public VerificationCodeServiceImpl(IdentityProperties properties,
                                       VerificationCodeStore store,
                                       RateLimitService rateLimitService,
                                       CryptoService cryptoService,
                                       MaskService maskService,
                                       AuditEventService auditEventService) {
        this.properties = properties;
        this.store = store;
        this.rateLimitService = rateLimitService;
        this.cryptoService = cryptoService;
        this.maskService = maskService;
        this.auditEventService = auditEventService;
    }

    @Override
    public VerificationCodeSendResponse send(VerificationCodeSendRequest request) {
        String phoneDigest = cryptoService.phoneDigest(request.phone());
        rateLimitService.checkSend(phoneDigest);
        String code = properties.verificationCode().fixedEnabled()
                ? properties.verificationCode().fixedCode()
                : String.format("%06d", RANDOM.nextInt(1_000_000));
        store.save(request.purpose(), phoneDigest, cryptoService.codeDigest(code), 0);
        String maskedPhone = maskService.maskPhone(request.phone());
        auditEventService.record(AuditEventType.VERIFICATION_CODE_SENT, "SUCCESS",
                null, maskedPhone, null, request.purpose().name());
        return new VerificationCodeSendResponse(request.purpose(), maskedPhone,
                properties.verificationCode().ttlSeconds(),
                rateLimitService.sendIntervalSeconds());
    }

    @Override
    public void consume(VerificationPurpose purpose, String phone, String code) {
        store.verifyAndConsume(purpose, cryptoService.phoneDigest(phone),
                cryptoService.codeDigest(code));
    }

    @Override
    public void clear(VerificationPurpose purpose, String phone) {
        store.delete(purpose, cryptoService.phoneDigest(phone));
    }
}
