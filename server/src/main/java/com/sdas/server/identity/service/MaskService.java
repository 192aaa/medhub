package com.sdas.server.identity.service;

/**
 * 敏感信息脱敏展示服务。
 *
 * <p>格式与 specs/001-patient-identity/contracts/ 示例逐字对齐。</p>
 */
public interface MaskService {

    /**
     * 手机号脱敏。
     *
     * @param phone 明文手机号
     * @return 如 138****8000
     */
    String maskPhone(String phone);

    /**
     * 姓名脱敏。
     *
     * @param name 明文姓名
     * @return 如 张*
     */
    String maskName(String name);

    /**
     * 身份证号脱敏。
     *
     * @param idCardNumber 明文证件号
     * @return 如 110***********002X
     */
    String maskIdCard(String idCardNumber);

    /**
     * 证件号密文字节转字符串（供加密服务复用）。
     *
     * @param value 明文
     * @return UTF-8 字节
     */
    byte[] utf8Bytes(String value);
}
