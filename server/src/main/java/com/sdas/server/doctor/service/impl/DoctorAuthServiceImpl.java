package com.sdas.server.doctor.service.impl;

import com.sdas.server.appointment.entity.Doctor;
import com.sdas.server.appointment.repository.DoctorRepository;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.doctor.dto.LoginRequest;
import com.sdas.server.doctor.dto.LoginResponse;
import com.sdas.server.doctor.entity.DoctorAccount;
import com.sdas.server.doctor.model.DoctorAccountStatus;
import com.sdas.server.doctor.repository.DoctorAccountRepository;
import com.sdas.server.doctor.service.DoctorAuthService;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import com.sdas.server.identity.service.JwtTokenService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 医生登录实现：校验 doctor_account（BCrypt + ACTIVE），签发含「医生」角色的 JWT，
 * 失败统一 AUTHENTICATION_FAILED，写审计 DOCTOR_LOGIN。
 */
@Service
public class DoctorAuthServiceImpl implements DoctorAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int TOKEN_BYTES = 32;

    private final DoctorAccountRepository accountRepository;

    private final DoctorRepository doctorRepository;

    private final JwtTokenService jwtTokenService;

    private final AuditEventService auditEventService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 构造医生登录服务。
     *
     * @param accountRepository 医生账号仓储
     * @param doctorRepository  医生档案仓储
     * @param jwtTokenService   JWT 服务
     * @param auditEventService 审计
     */
    public DoctorAuthServiceImpl(DoctorAccountRepository accountRepository,
                                 DoctorRepository doctorRepository,
                                 JwtTokenService jwtTokenService,
                                 AuditEventService auditEventService) {
        this.accountRepository = accountRepository;
        this.doctorRepository = doctorRepository;
        this.jwtTokenService = jwtTokenService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Optional<DoctorAccount> found = accountRepository.findByUsername(request.username());
        boolean valid = found.isPresent()
                && found.get().status() == DoctorAccountStatus.ACTIVE
                && passwordEncoder.matches(request.password(), found.get().passwordHash());
        if (!valid) {
            auditEventService.record(AuditEventType.DOCTOR_LOGIN, "FAILURE", null, null, null, null);
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        DoctorAccount account = found.get();
        Doctor doctor = doctorRepository.findById(account.doctorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCTOR_ACCOUNT_NOT_FOUND));
        long sessionId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        String refreshToken = randomToken();
        String accessToken = jwtTokenService.issueDoctor(account.doctorId(), sessionId);
        auditEventService.record(AuditEventType.DOCTOR_LOGIN, "SUCCESS", null,
                account.nameMask(), null, null);
        return new LoginResponse(accessToken, refreshToken, account.doctorId(), account.nameMask(),
                doctor.title(), doctor.departmentId(), jwtTokenService.accessTokenTtlSeconds());
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
