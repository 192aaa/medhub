package com.sdas.server.identity.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sdas.server.identity.service.MaskService;
import com.sdas.server.identity.service.impl.MaskServiceImpl;
import org.junit.jupiter.api.Test;

/**
 * T036：脱敏格式与 contracts/ 示例逐字一致。
 */
class MaskServiceTest {

    private final MaskService service = new MaskServiceImpl();

    @Test
    void maskPhoneMatchesContract() {
        assertEquals("138****8000", service.maskPhone("13800138000"));
    }

    @Test
    void maskNameMatchesContract() {
        assertEquals("张*", service.maskName("张三"));
    }

    @Test
    void maskIdCardMatchesContract() {
        assertEquals("110***********002X", service.maskIdCard("11010519491231002X"));
    }

    @Test
    void invalidInputDoesNotThrow() {
        assertEquals("", service.maskPhone(null));
        assertEquals("", service.maskName(""));
        assertEquals("", service.maskIdCard("123"));
    }
}
