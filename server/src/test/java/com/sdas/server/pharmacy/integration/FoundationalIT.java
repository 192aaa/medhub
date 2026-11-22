package com.sdas.server.pharmacy.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.PharmacyProperties;
import com.sdas.server.pharmacy.support.PharmacyTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 地基集成测试（T027）。
 *
 * <p>断言：2 张新表与 {@code qr_code} 扩容、新索引、V7 迁移成功、6 个错误码文案与
 * {@code data-model.md} 1.6 逐字一致、{@code PharmacyProperties} 绑定成功、
 * test/prod 不加载 {@code db/seed}。此时尚无任何新端点。</p>
 */
class FoundationalIT extends PharmacyTestSupport {

    /** data-model.md 1.6 的 code → message 逐字映射。 */
    private static final Map<ErrorCode, String> CONTRACT_ERROR_CODES = Map.of(
            ErrorCode.PHARMACY_ROLE_REQUIRED, "仅药店工作人员可访问该功能",
            ErrorCode.PHARMACY_ACCOUNT_NOT_FOUND, "药店账号不存在或已禁用",
            ErrorCode.INVALID_VERIFICATION_PROOF, "验真凭证无效，请重新扫码",
            ErrorCode.PRESCRIPTION_NOT_FOUND, "处方不存在或不属于当前患者",
            ErrorCode.PRESCRIPTION_NOT_SIGNED, "处方未签名，无法取药",
            ErrorCode.PRESCRIPTION_REVOKED, "处方已作废，无法取药");

    @Autowired
    private PharmacyProperties pharmacyProperties;

    @Test
    void schemaContainsNewTablesAndPrescriptionChanges() {
        assertEquals(1, countOf(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pharmacy_account'"));
        assertEquals(1, countOf(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'prescription_dispense_record'"));
        assertEquals(1024, countOf(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prescription' "
                        + "AND COLUMN_NAME = 'qr_code'"));
        assertTrue(countOf(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prescription' "
                        + "AND INDEX_NAME = 'idx_prescription_patient'") >= 1);
        assertEquals(1, countOf(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'prescription_dispense_record' "
                        + "AND INDEX_NAME = 'uk_dispense_prescription' "
                        + "AND NON_UNIQUE = 0"));
    }

    @Test
    void migrationV7AppliedSuccessfully() {
        assertEquals(1, countOf(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '7' AND success = 1"));
    }

    @Test
    void contractErrorCodesMatchDataModelVerbatim() {
        assertEquals(6, CONTRACT_ERROR_CODES.size());
        CONTRACT_ERROR_CODES.forEach((code, message) -> {
            assertEquals(message, code.getMessage());
            assertEquals(
                    switch (code) {
                        case PHARMACY_ROLE_REQUIRED -> 403;
                        case PHARMACY_ACCOUNT_NOT_FOUND, PRESCRIPTION_NOT_FOUND -> 404;
                        default -> 400;
                    }, code.getCode());
        });
    }

    @Test
    void pharmacyPropertiesAreBound() {
        assertNotNull(pharmacyProperties.qr());
        assertEquals("v1", pharmacyProperties.qr().tokenVersion());
        assertEquals(12, pharmacyProperties.qr().verifyCodeLength());
        assertEquals(480, pharmacyProperties.qr().imageSize());
        assertEquals("M", pharmacyProperties.qr().errorCorrection());
        assertEquals(30, pharmacyProperties.qr().tokenGraceDays());
        assertTrue(pharmacyProperties.qr().verifyUrlTemplate().contains("%s"));
    }

    @Test
    void testProfileDoesNotLoadDevSeed() {
        // 仅断言 V8 种子未被执行；不断言 pharmacy_account 行数，避免与其他用例的执行顺序耦合
        assertEquals(0, countOf(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '8'"));
    }

    private int countOf(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        return value == null ? 0 : value.intValue();
    }
}
