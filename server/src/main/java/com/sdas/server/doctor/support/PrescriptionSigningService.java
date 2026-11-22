package com.sdas.server.doctor.support;

import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.DoctorProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 电子处方签名服务（JDK 内置 {@code SHA256withRSA}，零新增依赖）。
 *
 * <p>密钥来源为 {@code sdas.doctor.signature.key-store-path/alias/key-store-password} 指向的
 * JKS/PKCS12 密钥库，签名私钥由服务端托管、医生本地不持有，因此重启后历史签名仍可验真。
 * {@code prod} 缺失密钥库启动即失败；{@code dev/test} 缺失时自动生成自签名证书并写回，
 * 便于本地与 CI 直接运行（specs/004-prescription-dispensing/research.md R-03）。</p>
 */
@Service
public class PrescriptionSigningService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrescriptionSigningService.class);

    private static final String PROD_PROFILE = "prod";

    private static final int KEY_SIZE = 2048;

    private final DoctorProperties properties;

    private final Environment environment;

    private volatile KeyStore keyStore;

    /**
     * 构造签名服务。
     *
     * @param properties  医生接诊配置
     * @param environment Spring 环境（判定 {@code prod} profile）
     */
    public PrescriptionSigningService(DoctorProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * 启动即加载密钥库：配置错误在启动期暴露，而非等到医生开方时。
     */
    @Override
    public void afterPropertiesSet() {
        keyStore();
    }

    /**
     * 对处方规范化摘要签名。
     *
     * @param content 规范化摘要原文
     * @return Base64 签名值
     */
    public String sign(String content) {
        return Base64.getEncoder().encodeToString(signRaw(content));
    }

    /**
     * 对原文签名并返回原始签名字节（供令牌层做 Base64Url 编码）。
     *
     * @param content 待签名原文
     * @return 原始签名值（RSA-2048 为 256 字节）
     */
    public byte[] signRaw(String content) {
        try {
            Signature signature = Signature.getInstance(algorithm());
            signature.initSign(privateKey());
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (BusinessException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            LOGGER.error("Prescription signing failed", exception);
            throw new BusinessException(ErrorCode.PRESCRIPTION_SIGN_FAILED);
        }
    }

    /**
     * 验真（供测试与下游核验）。
     *
     * @param content       规范化摘要原文
     * @param signatureText Base64 签名值
     * @return 是否通过验真
     */
    public boolean verify(String content, String signatureText) {
        try {
            return verifyRaw(content, Base64.getDecoder().decode(signatureText));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 用原始签名字节验签。
     *
     * @param content        原文
     * @param signatureBytes 原始签名值
     * @return 是否通过
     */
    public boolean verifyRaw(String content, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance(algorithm());
            signature.initVerify(publicKey());
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException | RuntimeException exception) {
            return false;
        }
    }

    /**
     * 导出签名公钥（可离机验签，供药店验真与运维核对）。
     *
     * @return 密钥库证书公钥
     */
    public PublicKey publicKey() {
        try {
            Certificate certificate = keyStore().getCertificate(alias());
            if (certificate == null) {
                throw new BusinessException(ErrorCode.PRESCRIPTION_SIGN_FAILED);
            }
            return certificate.getPublicKey();
        } catch (BusinessException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            LOGGER.error("Failed to read public key from keystore", exception);
            throw new BusinessException(ErrorCode.PRESCRIPTION_SIGN_FAILED);
        }
    }

    private PrivateKey privateKey() {
        try {
            Key key = keyStore().getKey(alias(), password());
            if (key instanceof PrivateKey privateKey) {
                return privateKey;
            }
        } catch (GeneralSecurityException exception) {
            LOGGER.error("Failed to read private key from keystore", exception);
        }
        throw new BusinessException(ErrorCode.PRESCRIPTION_SIGN_FAILED);
    }

    private KeyStore keyStore() {
        KeyStore local = keyStore;
        if (local == null) {
            synchronized (this) {
                local = keyStore;
                if (local == null) {
                    local = loadKeyStore();
                    keyStore = local;
                }
            }
        }
        return local;
    }

    private KeyStore loadKeyStore() {
        DoctorProperties.Signature config = properties.signature();
        String location = config == null ? null : config.keyStorePath();
        if (location == null || location.isBlank()) {
            LOGGER.error("Prescription signing keystore path is not configured");
            throw new BusinessException(ErrorCode.PRESCRIPTION_SIGN_FAILED);
        }
        Path file = Path.of(location);
        char[] password = password();
        try {
            if (Files.isRegularFile(file)) {
                KeyStore loaded = KeyStore.getInstance(typeOf(location));
                try (InputStream input = Files.newInputStream(file)) {
                    loaded.load(input, password);
                }
                LOGGER.info("Prescription signing keystore loaded: {}", file);
                return loaded;
            }
            if (isProd()) {
                LOGGER.error("Prescription signing keystore is required in prod but missing: {}",
                        file);
                throw new BusinessException(ErrorCode.PRESCRIPTION_SIGN_FAILED);
            }
            return generateKeyStore(file, config, password);
        } catch (BusinessException exception) {
            throw exception;
        } catch (GeneralSecurityException | IOException exception) {
            LOGGER.error("Failed to load prescription signing keystore: {}", file, exception);
            throw new BusinessException(ErrorCode.PRESCRIPTION_SIGN_FAILED);
        }
    }

    private KeyStore generateKeyStore(Path file, DoctorProperties.Signature config, char[] password)
            throws GeneralSecurityException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_SIZE);
        KeyPair keyPair = generator.generateKeyPair();
        X509Certificate certificate = SelfSignedCertificate.generate(keyPair, config.keyAlias());
        KeyStore generated = KeyStore.getInstance(typeOf(config.keyStorePath()));
        generated.load(null, password);
        generated.setKeyEntry(config.keyAlias(), keyPair.getPrivate(), password,
                new Certificate[] {certificate});
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream output = Files.newOutputStream(file)) {
            generated.store(output, password);
        }
        LOGGER.warn("Prescription signing keystore auto-generated (dev/test only): {}", file);
        return generated;
    }

    private String algorithm() {
        return properties.signature().algorithm();
    }

    private String alias() {
        return properties.signature().keyAlias();
    }

    private char[] password() {
        String password = properties.signature().keyStorePassword();
        return password == null ? new char[0] : password.toCharArray();
    }

    private boolean isProd() {
        return environment != null
                && Arrays.stream(environment.getActiveProfiles()).anyMatch(PROD_PROFILE::equals);
    }

    private static String typeOf(String location) {
        String lower = location.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".p12") || lower.endsWith(".pfx") || lower.endsWith(".pkcs12")) {
            return "PKCS12";
        }
        return "JKS";
    }
}
