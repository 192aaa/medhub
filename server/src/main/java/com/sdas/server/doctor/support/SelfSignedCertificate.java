package com.sdas.server.doctor.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 自签名 X.509 v3 证书生成器（仅用 JDK，不引入 BouncyCastle）。
 *
 * <p>用途：{@code dev/test} 首次启动若密钥库文件不存在，需为自动生成的 RSA 密钥对补一张
 * 自签名证书，才能写入 JKS/PKCS12——密钥库的私钥条目必须携带证书链。生产环境不使用本类，
 * 密钥库由部署方注入（见 specs/004-prescription-dispensing/research.md R-03）。</p>
 */
final class SelfSignedCertificate {

    /** sha256WithRSAEncryption 的 DER AlgorithmIdentifier（1.2.840.113549.1.1.11 + NULL 参数）。 */
    private static final byte[] SHA256_WITH_RSA = {
        0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7,
        0x0D, 0x01, 0x01, 0x0B, 0x05, 0x00};

    /** commonName(2.5.4.3) 的 DER OID。 */
    private static final byte[] COMMON_NAME_OID = {0x06, 0x03, 0x55, 0x04, 0x03};

    /** 版本值 2 即 X.509 v3，编码为 [0] EXPLICIT INTEGER 2。 */
    private static final byte[] VERSION_V3 = {(byte) 0xA0, 0x03, 0x02, 0x01, 0x02};

    private static final DateTimeFormatter UTC_TIME =
            DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 有效期（自签证书仅供进程内验签，取较长年限避免过期干扰）。 */
    private static final long VALID_DAYS = 3650L;

    private SelfSignedCertificate() {
    }

    /**
     * 为给定密钥对生成自签名证书。
     *
     * @param keyPair    密钥对（RSA 2048）
     * @param commonName 证书主体 CN，通常取密钥别名
     * @return 自签名证书
     */
    static X509Certificate generate(KeyPair keyPair, String commonName) {
        try {
            Instant now = Instant.now();
            byte[] name = sequence(set(sequence(COMMON_NAME_OID, utf8(commonName))));
            byte[] validity = sequence(
                    utcTime(now.minus(1, ChronoUnit.DAYS)),
                    utcTime(now.plus(VALID_DAYS, ChronoUnit.DAYS)));
            byte[] tbsCertificate = sequence(VERSION_V3, integer(serialNumber()), SHA256_WITH_RSA,
                    name, validity, name, keyPair.getPublic().getEncoded());
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(tbsCertificate);
            byte[] encoded = sequence(tbsCertificate, SHA256_WITH_RSA,
                    bitString(signer.sign()));
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(encoded));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to generate self-signed certificate", exception);
        }
    }

    private static BigInteger serialNumber() {
        return new BigInteger(64, RANDOM).add(BigInteger.ONE);
    }

    private static byte[] utcTime(Instant instant) {
        return tag(0x17, UTC_TIME.format(instant).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] utf8(String value) {
        return tag(0x0C, value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] integer(BigInteger value) {
        return tag(0x02, value.toByteArray());
    }

    private static byte[] bitString(byte[] value) {
        byte[] content = new byte[value.length + 1];
        content[0] = 0;
        System.arraycopy(value, 0, content, 1, value.length);
        return tag(0x03, content);
    }

    private static byte[] sequence(byte[]... parts) {
        return tag(0x30, concat(parts));
    }

    private static byte[] set(byte[]... parts) {
        return tag(0x31, concat(parts));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    private static byte[] tag(int identifier, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(identifier);
        if (content.length < 0x80) {
            out.write(content.length);
        } else {
            byte[] magnitude = BigInteger.valueOf(content.length).toByteArray();
            int offset = magnitude[0] == 0 ? 1 : 0;
            out.write(0x80 | (magnitude.length - offset));
            out.write(magnitude, offset, magnitude.length - offset);
        }
        out.write(content, 0, content.length);
        return out.toByteArray();
    }
}
