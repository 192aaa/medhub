package com.sdas.server.identity.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类：默认 Testcontainers（MySQL 8 + Redis 3.2）+ MockMvc。
 *
 * <p>宪法原则 VI：Flyway 自动迁移必须启用，禁止 spring.flyway.enabled=false；
 * test/prod 共用 db/migration，db/seed 不加载。
 * 开发机无 Docker 时可设 {@code IDENTITY_TEST_USE_LOCAL_INFRA=true}，改用本机
 * MySQL 8 / Redis 3.2.100 直连（版本与容器一致），仍强制启用 Flyway 与同一套 V1 脚本；
 * 该豁免已登记在 plan.md Complexity Tracking。CI 保持默认容器模式。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IdentityTestSupport {

    /** 无 Docker 环境下的本机基础设施开关。 */
    protected static final boolean USE_LOCAL_INFRA = Boolean.parseBoolean(
            System.getenv().getOrDefault("IDENTITY_TEST_USE_LOCAL_INFRA", "false"));

    /** MySQL 8 容器，与生产同套 Flyway 脚本。 */
    @Container
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("cloud_consultation_test");

    /** Redis 3.2 协议族容器。 */
    @Container
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:3.2-alpine"))
                    .withExposedPorts(6379);

    static {
        if (!USE_LOCAL_INFRA) {
            MYSQL.start();
            REDIS.start();
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    /**
     * 注入数据源与 4 个测试密钥：容器模式用容器地址，本机模式沿用
     * application-test.yml 的同版本本机实例；两种模式都强制 Flyway 只加载 db/migration。
     *
     * @param registry 动态属性注册表
     */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (!USE_LOCAL_INFRA) {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
            registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
            registry.add("spring.data.redis.host", REDIS::getHost);
            registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        }
        registry.add("sdas.identity.jwt.secret",
                () -> "test-only-jwt-secret-please-replace-in-production-000000");
        registry.add("sdas.identity.crypto.id-card-secret",
                () -> "test-only-id-card-hmac-secret-000000");
        registry.add("sdas.identity.crypto.phone-secret",
                () -> "test-only-phone-hmac-secret-000000");
        registry.add("sdas.identity.crypto.aes-key",
                () -> "dGVzdC1vbmx5LWFlcy1rZXktMzItYnl0ZXMtMDAwMDA=");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }
}
