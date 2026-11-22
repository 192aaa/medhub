package com.sdas.server.identity.unit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.service.CryptoService;
import com.sdas.server.identity.service.impl.CryptoServiceImpl;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T036：摘要一致性、AES-GCM 往返、常量时间比较。
 */
class CryptoServiceTest {

    private static final String AES_KEY = "ZGV2LW9ubHktYWVzLWtleS0zMi1ieXRlcy0wMDAwMDA=";

    private CryptoService service;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = mock(IdentityProperties.class);
        when(properties.crypto()).thenReturn(new IdentityProperties.Crypto(
                "id-card-secret", "phone-secret", AES_KEY));
        service = new CryptoServiceImpl(properties);
    }

    @Test
    void digestsAreDeterministicAndDistinct() {
        assertEquals(service.phoneDigest("13800138000"), service.phoneDigest("13800138000"));
        assertFalse(service.phoneDigest("13800138000")
                .equals(service.phoneDigest("13800138001")));
        assertEquals(64, service.idCardDigest("11010519491231002X").length());
        assertEquals(64, service.codeDigest("888888").length());
    }

    @Test
    void aesGcmRoundTrip() {
        byte[] plain = "13800138000".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = service.encrypt(plain);
        assertFalse(java.util.Arrays.equals(plain, cipher));
        assertArrayEquals(plain, service.decrypt(cipher));
    }

    @Test
    void constantTimeEquals() {
        assertTrue(service.constantTimeEquals("abc", "abc"));
        assertFalse(service.constantTimeEquals("abc", "abd"));
        assertFalse(service.constantTimeEquals(null, "abc"));
    }
}
