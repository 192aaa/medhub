package com.sdas.server.doctor.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.DoctorIdExtractor;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.identity.service.DoctorTokenClaims;
import com.sdas.server.identity.service.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

/** DoctorIdExtractor 单测（T023）：合法医生令牌、缺失头、患者令牌拒绝。 */
class DoctorIdExtractorTest {

    @Test
    void extractDoctorTokenReturnsClaims() {
        JwtTokenService jwt = mock(JwtTokenService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DoctorTokenClaims claims = new DoctorTokenClaims(101L, 20001L);
        when(jwt.parseDoctor("token")).thenReturn(claims);
        when(redis.hasKey(anyString())).thenReturn(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token");

        DoctorIdExtractor extractor = new DoctorIdExtractor(jwt, redis);
        assertEquals(claims, extractor.extract(request));
    }

    @Test
    void missingHeaderThrowsDoctorRoleRequired() {
        JwtTokenService jwt = mock(JwtTokenService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        DoctorIdExtractor extractor = new DoctorIdExtractor(jwt, redis);
        BusinessException exception =
                assertThrows(BusinessException.class, () -> extractor.extract(request));
        assertEquals(ErrorCode.DOCTOR_ROLE_REQUIRED, exception.getErrorCode());
    }

    @Test
    void revokedSessionThrowsUnauthorized() {
        JwtTokenService jwt = mock(JwtTokenService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(jwt.parseDoctor("token")).thenReturn(new DoctorTokenClaims(101L, 20001L));
        when(redis.hasKey(anyString())).thenReturn(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token");

        DoctorIdExtractor extractor = new DoctorIdExtractor(jwt, redis);
        BusinessException exception =
                assertThrows(BusinessException.class, () -> extractor.extract(request));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }
}
