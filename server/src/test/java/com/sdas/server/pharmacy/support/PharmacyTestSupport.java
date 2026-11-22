package com.sdas.server.pharmacy.support;

import com.sdas.server.doctor.support.PrescriptionSigningService;
import com.sdas.server.identity.service.JwtTokenService;
import com.sdas.server.identity.support.IdentityTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 药店端集成测试基类（T006）。
 *
 * <p>复用 001 的 {@link IdentityTestSupport}（Testcontainers MySQL 8 + Redis + MockMvc，或
 * {@code IDENTITY_TEST_USE_LOCAL_INFRA=true} 的本机基础设施），Flyway 强制只加载
 * {@code db/migration}；test/prod 不加载 {@code db/seed}，故所有演示数据由
 * {@link PharmacyFixtures} 幂等预置。</p>
 */
public abstract class PharmacyTestSupport extends IdentityTestSupport {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected JwtTokenService jwtTokenService;

    @Autowired
    protected PrescriptionSigningService signingService;

    @Autowired
    protected PrescriptionQrTokenService qrTokenService;

    /**
     * 幂等夹具入口。
     *
     * @return 夹具实例
     */
    protected PharmacyFixtures fixtures() {
        return new PharmacyFixtures(jdbcTemplate, signingService, qrTokenService);
    }

}
