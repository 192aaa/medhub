package com.sdas.server.config;

import java.util.Arrays;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动期安全校验（D1 fail-fast）：prod profile 下禁止启用固定验证码。
 *
 * <p>依据 specs/001-patient-identity/plan.md D1 与 research.md R-01。</p>
 */
@Component
public class IdentityStartupValidator implements InitializingBean {

    private static final String PROD_PROFILE = "prod";

    private final Environment environment;

    private final IdentityProperties properties;

    /**
     * 构造启动校验器。
     *
     * @param environment Spring 环境
     * @param properties  患者身份模块配置
     */
    public IdentityStartupValidator(Environment environment, IdentityProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        boolean prod = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(PROD_PROFILE::equals);
        boolean fixedEnabled = properties.verificationCode() != null
                && properties.verificationCode().fixedEnabled();
        if (prod && fixedEnabled) {
            throw new IllegalStateException(
                    "Fixed verification code must be disabled in prod profile");
        }
    }
}
