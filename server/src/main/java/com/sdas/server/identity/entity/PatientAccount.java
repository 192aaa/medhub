package com.sdas.server.identity.entity;

import com.sdas.server.identity.model.AccountStatus;
import com.sdas.server.identity.model.AuthStatus;
import java.time.LocalDateTime;

/**
 * 患者账户实体。
 *
 * <p>字段唯一来源：specs/001-patient-identity/data-model.md 3.1。</p>
 *
 * @param id             患者主键
 * @param phoneHash      手机号 HMAC 摘要
 * @param phoneCipher    手机号 AES-GCM 密文
 * @param phoneMask      脱敏手机号
 * @param passwordHash   BCrypt 哈希
 * @param authStatus     认证状态
 * @param accountStatus  账户状态
 * @param registeredAt   注册时间
 * @param verifiedAt     认证通过时间
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 * @param version        乐观锁版本
 */
public record PatientAccount(
        Long id,
        String phoneHash,
        byte[] phoneCipher,
        String phoneMask,
        String passwordHash,
        AuthStatus authStatus,
        AccountStatus accountStatus,
        LocalDateTime registeredAt,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {
}
