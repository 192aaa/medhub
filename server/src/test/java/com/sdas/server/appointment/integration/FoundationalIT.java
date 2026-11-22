package com.sdas.server.appointment.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sdas.server.appointment.support.AppointmentTestSupport;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.AppointmentProperties;
import com.sdas.server.config.ImageStorageProperties;
import com.sdas.server.config.TriageProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

/**
 * 002 地基自检（tasks.md T024）：表结构、Flyway 迁移隔离、16 个错误码文案、配置绑定。
 */
class FoundationalIT extends AppointmentTestSupport {

    private static final List<String> TABLES = List.of(
            "department", "doctor", "doctor_schedule", "department_disease_kb",
            "patient_history_visit", "appointment", "payment_record", "consultation",
            "consultation_image", "ai_triage_session");

    @Autowired
    private Environment environment;

    @Autowired
    private AppointmentProperties appointmentProperties;

    @Autowired
    private TriageProperties triageProperties;

    @Autowired
    private ImageStorageProperties imageStorageProperties;

    @Test
    void allTenTablesExist() {
        for (String table : TABLES) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM information_schema"
                    + ".tables WHERE table_schema = DATABASE() AND table_name = ?", Integer.class,
                    table);
            assertNotNull(count);
            assertEquals(1, count.intValue(), "缺少表：" + table);
        }
    }

    @Test
    void flywayLocationsExcludeDemoSeed() throws Exception {
        assertEquals("classpath:db/migration",
                environment.getProperty("spring.flyway.locations"));
        assertFalse("false".equalsIgnoreCase(environment.getProperty("spring.flyway.enabled")),
                "禁止通过 spring.flyway.enabled=false 绕过迁移");

        // 生产沿用 Spring Boot 默认类路径 classpath:db/migration，且不得追加演示种子
        String prod = new ClassPathResource("application-prod.yml")
                .getContentAsString(StandardCharsets.UTF_8);
        assertFalse(prod.contains("classpath:db/seed"), "prod 禁止加载演示种子");
        assertFalse(prod.contains("flyway"), "prod 不得覆盖 Flyway 默认迁移配置");

        // dev 是唯一允许加载 db/seed 的环境
        String dev = new ClassPathResource("application-dev.yml")
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(dev.contains("classpath:db/migration") && dev.contains("classpath:db/seed"));
    }

    @Test
    void sixteenNewErrorCodesMatchDataModel() {
        assertEquals(404, ErrorCode.DEPARTMENT_NOT_FOUND.getCode());
        assertEquals("科室不存在或已停用", ErrorCode.DEPARTMENT_NOT_FOUND.getMessage());
        assertEquals(404, ErrorCode.DOCTOR_NOT_FOUND.getCode());
        assertEquals("医生不存在", ErrorCode.DOCTOR_NOT_FOUND.getMessage());
        assertEquals(404, ErrorCode.APPOINTMENT_NOT_FOUND.getCode());
        assertEquals("挂号单不存在或不属于当前患者", ErrorCode.APPOINTMENT_NOT_FOUND.getMessage());
        assertEquals(404, ErrorCode.CONSULTATION_NOT_FOUND.getCode());
        assertEquals("问诊单不存在或不属于当前患者", ErrorCode.CONSULTATION_NOT_FOUND.getMessage());
        assertEquals(400, ErrorCode.INVALID_COMPLAINT_FORMAT.getCode());
        assertEquals("主诉长度须为 10-200 个字符", ErrorCode.INVALID_COMPLAINT_FORMAT.getMessage());
        assertEquals(400, ErrorCode.INVALID_DESCRIPTION_FORMAT.getCode());
        assertEquals("描述长度超出上限", ErrorCode.INVALID_DESCRIPTION_FORMAT.getMessage());
        assertEquals(400, ErrorCode.INVALID_IMAGE_FORMAT.getCode());
        assertEquals("图片格式不支持", ErrorCode.INVALID_IMAGE_FORMAT.getMessage());
        assertEquals(400, ErrorCode.INVALID_IMAGE_SIZE.getCode());
        assertEquals("单张图片不得超过 5 MB", ErrorCode.INVALID_IMAGE_SIZE.getMessage());
        assertEquals(400, ErrorCode.TOO_MANY_IMAGES.getCode());
        assertEquals("图片数量不得超过 9 张", ErrorCode.TOO_MANY_IMAGES.getMessage());
        assertEquals(400, ErrorCode.INVALID_PAYMENT_AMOUNT.getCode());
        assertEquals("支付金额与应缴金额不一致", ErrorCode.INVALID_PAYMENT_AMOUNT.getMessage());
        assertEquals(400, ErrorCode.INVALID_APPOINTMENT_STATUS.getCode());
        assertEquals("当前挂号单状态不允许该操作", ErrorCode.INVALID_APPOINTMENT_STATUS.getMessage());
        assertEquals(400, ErrorCode.INVALID_SCHEDULE.getCode());
        assertEquals("所选医生或时段不可预约", ErrorCode.INVALID_SCHEDULE.getMessage());
        assertEquals(403, ErrorCode.REVISIT_NOT_ELIGIBLE.getCode());
        assertEquals("请先完成实名认证或确认复诊资格", ErrorCode.REVISIT_NOT_ELIGIBLE.getMessage());
        assertEquals(403, ErrorCode.CONSULTATION_FORBIDDEN.getCode());
        assertEquals("禁止访问他人问诊资料", ErrorCode.CONSULTATION_FORBIDDEN.getMessage());
        assertEquals(409, ErrorCode.PAYMENT_CONFLICT.getCode());
        assertEquals("该挂号单已支付，请勿重复支付", ErrorCode.PAYMENT_CONFLICT.getMessage());
        assertEquals(400, ErrorCode.PAYMENT_FAILED.getCode());
        assertEquals("支付失败，请重试", ErrorCode.PAYMENT_FAILED.getMessage());
    }

    @Test
    void modulePropertiesAreBound() {
        assertTrue(appointmentProperties.mockPaymentEnabled());
        assertEquals(900, appointmentProperties.paymentTimeoutSeconds());
        assertEquals(5000, triageProperties.timeoutMillis());
        assertEquals("FULL_DEPARTMENT_LIST", triageProperties.fallback());
        assertNotNull(triageProperties.model());
        assertEquals(5_242_880, imageStorageProperties.maxSizeBytes());
        assertEquals(9, imageStorageProperties.maxCount());
        assertEquals("/static/consultation-images", imageStorageProperties.servePath());
        assertTrue(imageStorageProperties.allowedContentTypes().contains("image/png"));
    }
}
