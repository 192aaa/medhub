package com.sdas.server.doctor.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.appointment.entity.Doctor;
import com.sdas.server.appointment.model.DoctorOnlineStatus;
import com.sdas.server.appointment.repository.DoctorRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.doctor.dto.LoginRequest;
import com.sdas.server.doctor.dto.LoginResponse;
import com.sdas.server.doctor.entity.DoctorAccount;
import com.sdas.server.doctor.model.DoctorAccountStatus;
import com.sdas.server.doctor.repository.DoctorAccountRepository;
import com.sdas.server.doctor.service.impl.DoctorAuthServiceImpl;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.JwtTokenService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** DoctorAuthService 单测（T028）：登录成功、模糊失败、禁用账号拒绝。 */
class DoctorAuthServiceTest {

    /** 运行时生成，确保与明文 "password" 匹配。 */
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(10).encode("password");

    private DoctorAccountRepository accountRepository;
    private DoctorRepository doctorRepository;
    private JwtTokenService jwtTokenService;
    private DoctorAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(DoctorAccountRepository.class);
        doctorRepository = mock(DoctorRepository.class);
        jwtTokenService = mock(JwtTokenService.class);
        AuditEventService auditEventService = mock(AuditEventService.class);
        service = new DoctorAuthServiceImpl(accountRepository, doctorRepository,
                jwtTokenService, auditEventService);
    }

    @Test
    void loginSuccessReturnsProfile() {
        DoctorAccount account = new DoctorAccount(1L, "doctor_zhang", PASSWORD_HASH, 101L,
                "张*", DoctorAccountStatus.ACTIVE, null, null, 0);
        when(accountRepository.findByUsername("doctor_zhang")).thenReturn(Optional.of(account));
        Doctor doctor = new Doctor(101L, 1L, "张*", "主任医师", "示例医院", 15, "呼吸系统疾病",
                null, null, 1200, DoctorOnlineStatus.ONLINE, null, null, null);
        when(doctorRepository.findById(101L)).thenReturn(Optional.of(doctor));
        when(jwtTokenService.issueDoctor(eq(101L), anyLong())).thenReturn("access-token");
        when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(7200L);

        LoginResponse response = service.login(new LoginRequest("doctor_zhang", "password"));
        assertEquals(101L, response.doctorId());
        assertEquals("张*", response.nameMask());
        assertEquals("主任医师", response.title());
        assertEquals("access-token", response.accessToken());
    }

    @Test
    void loginFailureIsAmbiguous() {
        when(accountRepository.findByUsername("doctor_zhang")).thenReturn(Optional.empty());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("doctor_zhang", "wrong")));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
    }

    @Test
    void disabledAccountIsRejected() {
        DoctorAccount account = new DoctorAccount(1L, "doctor_zhang", PASSWORD_HASH, 101L,
                "张*", DoctorAccountStatus.DISABLED, null, null, 0);
        when(accountRepository.findByUsername("doctor_zhang")).thenReturn(Optional.of(account));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("doctor_zhang", "password")));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
    }
}
