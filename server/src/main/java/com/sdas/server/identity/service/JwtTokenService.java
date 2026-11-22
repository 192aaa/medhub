package com.sdas.server.identity.service;

/**
 * JWT access token 签发与解析（HS256，载荷仅含 patientId / sessionId / iat / exp）。
 */
public interface JwtTokenService {

    /**
     * 签发访问凭证。
     *
     * @param patientId 患者主键
     * @param sessionId 会话主键
     * @return JWT 字符串
     */
    String issue(long patientId, long sessionId);

    /**
     * 解析访问凭证，任何失败统一抛 UNAUTHORIZED。
     *
     * @param token JWT 字符串
     * @return 身份声明
     */
    TokenClaims parse(String token);

    /**
     * 签发医生访问凭证（载荷含「医生」角色）。
     *
     * @param doctorId  医生主键
     * @param sessionId 会话主键
     * @return JWT 字符串
     */
    String issueDoctor(long doctorId, long sessionId);

    /**
     * 解析医生访问凭证，角色不匹配或任何失败统一抛 DOCTOR_ROLE_REQUIRED。
     *
     * @param token JWT 字符串
     * @return 医生身份声明
     */
    DoctorTokenClaims parseDoctor(String token);

    /**
     * 签发药店访问凭证（载荷含「药店」角色）。
     *
     * @param pharmacyId 药店账号主键
     * @param sessionId  会话主键
     * @return JWT 字符串
     */
    String issuePharmacy(long pharmacyId, long sessionId);

    /**
     * 解析药店访问凭证，角色不匹配或任何失败统一抛 PHARMACY_ROLE_REQUIRED。
     *
     * @param token JWT 字符串
     * @return 药店身份声明
     */
    PharmacyTokenClaims parsePharmacy(String token);

    /** @return access token 有效期（秒） */
    long accessTokenTtlSeconds();
}
