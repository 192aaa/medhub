package com.sdas.server.identity.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.IdentityProperties;
import com.sdas.server.config.IdentityStartupValidator;
import com.sdas.server.identity.support.IdentityTestSupport;
import com.sdas.server.identity.support.RedisKeys;
import com.sdas.server.identity.support.VerificationCodeStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * T035 地基自检：表 / 索引 / 迁移隔离 / Redis 键模板 / 错误码文案 / prod fail-fast / CSRF。
 *
 * <p>继承 Testcontainers 基类，运行需 Docker；本任务与 T036 属地基自检，
 * 不承担用户故事测试先行职责。</p>
 */
class FoundationalIT extends IdentityTestSupport {

    private static final List<String> TABLES = List.of(
            "patient_account", "patient_identity", "patient_agreement_consent",
            "patient_session", "hospital_identity_whitelist", "security_audit_event");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private VerificationCodeStore verificationCodeStore;

    @Autowired
    private IdentityProperties properties;

    @Test
    void allSixTablesExist() {
        for (String table : TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            assertEquals(1, count);
        }
    }

    @Test
    void uniqueIndexesExist() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND index_name = 'uk_phone_hash'",
                Integer.class);
        assertTrue(count != null && count >= 1);
    }

    @Test
    void seedDirectoryIsNotLoadedInTest() {
        String locations = String.join(",", environment.getProperty(
                "spring.flyway.locations", "classpath:db/migration").split(","));
        assertTrue(locations.contains("classpath:db/migration"));
        assertTrue(!locations.contains("classpath:db/seed"));
    }

    @Test
    void allRedisKeyTemplatesUsePrefix() {
        assertEquals(6, verificationCodeStore.templates().size());
        for (String key : verificationCodeStore.templates()) {
            assertTrue(key.startsWith(RedisKeys.PREFIX));
        }
    }

    @Test
    void errorCodeMessagesMatchDataModel() {
        assertEquals("请先阅读并同意协议", ErrorCode.AGREEMENT_NOT_ACCEPTED.getMessage());
        assertEquals("账号或密码错误，或验证信息无效",
                ErrorCode.AUTHENTICATION_FAILED.getMessage());
        assertEquals("服务暂时不可用，请稍后重试",
                ErrorCode.SERVICE_UNAVAILABLE.getMessage());
        assertEquals(429, ErrorCode.RATE_LIMITED.getCode());
        assertEquals(403, ErrorCode.CSRF_TOKEN_MISMATCH.getCode());
    }

    @Test
    void prodProfileRejectsFixedCode() {
        MockEnvironment prod = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        IdentityProperties fixedEnabled = new IdentityProperties(
                new IdentityProperties.VerificationCode(true, "888888", 300, 60, 10, 5),
                properties.agreement(), properties.jwt(), properties.crypto(),
                properties.whitelist());
        IdentityStartupValidator validator = new IdentityStartupValidator(prod, fixedEnabled);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                validator::afterPropertiesSet);
    }

    @Test
    void missingCsrfHeaderReturns403() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/verification-codes")
                        .contentType("application/json")
                        .content("{\"phone\":\"13800138000\",\"purpose\":\"REGISTER\"}"))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(403));
    }
}
