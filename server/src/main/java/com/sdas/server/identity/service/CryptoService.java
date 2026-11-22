package com.sdas.server.identity.service;

/**
 * 敏感信息摘要与加解密服务。
 *
 * <p>密钥只从 IdentityProperties 注入；禁止明文写入日志、响应与审计（原则 IV）。</p>
 */
public interface CryptoService {

    /**
     * 手机号 HMAC-SHA256 摘要（登录查询与唯一约束用）。
     *
     * @param phone 明文手机号
     * @return 64 位十六进制摘要
     */
    String phoneDigest(String phone);

    /**
     * 身份证号 HMAC-SHA256 摘要（白名单比对与唯一约束用）。
     *
     * @param idCardNumber 明文证件号
     * @return 64 位十六进制摘要
     */
    String idCardDigest(String idCardNumber);

    /**
     * 验证码 HMAC-SHA256 摘要（Redis 只存摘要）。
     *
     * @param code 验证码
     * @return 64 位十六进制摘要
     */
    String codeDigest(String code);

    /**
     * AES-GCM 加密（返回 12 字节 IV + 密文）。
     *
     * @param plaintext 明文字节
     * @return 密文字节
     */
    byte[] encrypt(byte[] plaintext);

    /**
     * AES-GCM 解密。
     *
     * @param ciphertext encrypt 的输出
     * @return 明文字节
     */
    byte[] decrypt(byte[] ciphertext);

    /**
     * 常量时间字符串比较，用于 CSRF / 摘要比对。
     *
     * @param left  左值
     * @param right 右值
     * @return 是否相等
     */
    boolean constantTimeEquals(String left, String right);
}
