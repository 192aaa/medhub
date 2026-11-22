package com.sdas.server.identity.service.impl;

import com.sdas.server.config.IdentityProperties;
import com.sdas.server.identity.service.CryptoService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * HMAC-SHA256 摘要与 AES-GCM 加解密实现。
 *
 * <p>密钥来源：specs/001-patient-identity/data-model.md 第 6 节；
 * 算法与用途见 research.md R-04。</p>
 */
@Service
public class CryptoServiceImpl implements CryptoService {

    private static final int IV_LENGTH = 12;

    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityProperties properties;

    /**
     * 构造加密服务。
     *
     * @param properties 患者身份模块配置
     */
    public CryptoServiceImpl(IdentityProperties properties) {
        this.properties = properties;
    }

    @Override
    public String phoneDigest(String phone) {
        return hmac(properties.crypto().phoneSecret(), phone);
    }

    @Override
    public String idCardDigest(String idCardNumber) {
        return hmac(properties.crypto().idCardSecret(), idCardNumber);
    }

    @Override
    public String codeDigest(String code) {
        return hmac(properties.crypto().phoneSecret(), code);
    }

    @Override
    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] result = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);
            return result;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-GCM encryption failed", exception);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Invalid AES-GCM ciphertext");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[ciphertext.length - IV_LENGTH];
            System.arraycopy(ciphertext, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-GCM decryption failed", exception);
        }
    }

    @Override
    public boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey aesKey() {
        byte[] raw = Base64.getDecoder().decode(properties.crypto().aesKey());
        return new SecretKeySpec(raw, "AES");
    }

    private String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 digest failed", exception);
        }
    }
}
