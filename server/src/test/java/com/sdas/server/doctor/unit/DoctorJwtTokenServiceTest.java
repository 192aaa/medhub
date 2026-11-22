package com.sdas.server.doctor.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.service.DoctorTokenClaims;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.service.impl.JwtTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 医生 JWT 签发/解析单测（T016/T034）：医生令牌往返、患者令牌被拒。 */
class DoctorJwtTokenServiceTest {

    private static final String SECRET =
            "test-only-jwt-secret-please-replace-in-production-000000";

    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = mock(IdentityProperties.class);
        when(properties.jwt()).thenReturn(new IdentityProperties.Jwt(7200L, 604800L, SECRET));
        service = new JwtTokenServiceImpl(properties);
    }

    @Test
    void issueDoctorThenParseDoctorReturnsClaims() {
        String token = service.issueDoctor(101L, 20001L);
        DoctorTokenClaims claims = service.parseDoctor(token);
        assertEquals(101L, claims.doctorId());
        assertEquals(20001L, claims.sessionId());
    }

    @Test
    void patientTokenIsRejectedAsDoctorRoleRequired() {
        String patientToken = service.issue(10001L, 20001L);
        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.parseDoctor(patientToken));
        assertEquals(ErrorCode.DOCTOR_ROLE_REQUIRED, exception.getErrorCode());
    }

    @Test
    void tamperedDoctorTokenIsRejected() {
        String token = service.issueDoctor(101L, 20001L);
        String tampered = token.substring(0, token.length() - 2) + "aa";
        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.parseDoctor(tampered));
        assertEquals(ErrorCode.DOCTOR_ROLE_REQUIRED, exception.getErrorCode());
    }
}
