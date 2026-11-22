package com.sdas.server.pharmacy.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.PharmacyProperties;
import com.sdas.server.doctor.support.PrescriptionSigningService;
import com.sdas.server.identity.service.CryptoService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 处方二维码令牌签发与校验（{@code v1.<cipher>.<sig>}）。
 *
 * <p>唯一来源：specs/004-prescription-dispensing/data-model.md 1.7 与 plan.md
 * 「二维码防伪签名机制」。令牌 = AES-GCM 密文（处方 ID 不可枚举）+ RSA-SHA256 签名
 * （可离机验真），验签先于解密以规避填充/解密侧信道。</p>
 *
 * <p>令牌只经 POST 请求体传输，不写入日志：审计仅保留 SHA-256 前 16 位十六进制，
 * 核销记录的 {@code proof_digest} 保留完整 64 位十六进制。</p>
 */
@Service
public class PrescriptionQrTokenService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PrescriptionQrTokenService.class);

    /** 令牌结构：版本前缀 + 密文段 + 签名段（长度上限见 data-model 1.7）。 */
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("^v1\\.[A-Za-z0-9_-]{16,256}\\.[A-Za-z0-9_-]{300,400}$");

    private static final String VERSION = "v1";

    private static final int PAYLOAD_VERSION = 1;

    /** 令牌本身长度上限。 */
    private static final int MAX_TOKEN_LENGTH = 700;

    /** 拼接验真链接前缀后的完整 URL 上限，与 qr_code VARCHAR(1024) 对齐。 */
    private static final int MAX_LINK_LENGTH = 1024;

    /** 审计事件属性中的摘要长度（十六进制字符数）。 */
    private static final int AUDIT_DIGEST_LENGTH = 16;

    /** 令牌时间字段与 LocalDateTime 的换算时区，与应用默认时区一致。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final CryptoService cryptoService;

    private final PrescriptionSigningService signingService;

    private final PharmacyProperties properties;

    private final ObjectMapper objectMapper;

    /**
     * 构造令牌服务。
     *
     * @param cryptoService  AES-GCM 加解密服务
     * @param signingService RSA 签名服务（keystore 托管私钥）
     * @param properties     处方取药配置
     * @param objectMapper   JSON 序列化器
     */
    public PrescriptionQrTokenService(CryptoService cryptoService,
                                     PrescriptionSigningService signingService,
                                     PharmacyProperties properties,
                                     ObjectMapper objectMapper) {
        this.cryptoService = cryptoService;
        this.signingService = signingService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 签发令牌。
     *
     * @param prescriptionId 处方主键
     * @param validUntil     处方有效期截止（令牌 exp = 本值 + {@code token-grace-days}）
     * @return {@code v1.<cipher>.<sig>}
     */
    public String issue(long prescriptionId, LocalDateTime validUntil) {
        if (validUntil == null) {
            throw new IllegalStateException("valid_until is required to issue a qr token");
        }
        long issuedAt = Instant.now().getEpochSecond();
        // 令牌比处方多活 tokenGraceDays：处方过期后仍需能扫出「已过期」而非「凭证无效」
        long expiresAt = validUntil.plusDays(graceDays()).atZone(ZONE).toEpochSecond();
        TokenPayload payload = new TokenPayload(PAYLOAD_VERSION, prescriptionId, issuedAt,
                expiresAt);
        String cipher = URL_ENCODER.encodeToString(encodePayload(payload));
        String signingInput = VERSION + "." + cipher;
        String signature = URL_ENCODER.encodeToString(signingService.signRaw(signingInput));
        return ensureLength(signingInput + "." + signature);
    }

    /**
     * 校验令牌并取出处方主键。
     *
     * @param token 令牌字符串
     * @return 处方主键
     */
    public long verify(String token) {
        String normalized = extractToken(token);
        if (normalized.length() > MAX_TOKEN_LENGTH || !TOKEN_PATTERN.matcher(normalized).matches()) {
            throw invalidProof();
        }
        String[] parts = normalized.split("\\.");
        byte[] signature = decode(parts[2]);
        if (!signingService.verifyRaw(parts[0] + "." + parts[1], signature)) {
            throw invalidProof();
        }
        TokenPayload payload = decodePayload(decode(parts[1]));
        if (payload.v() != PAYLOAD_VERSION
                || Instant.now().getEpochSecond() > payload.exp()) {
            throw invalidProof();
        }
        return payload.pid();
    }

    /**
     * 取令牌签名段前 N 位作为手工输入验真码（N 取自配置，默认 12）。
     *
     * @param token 令牌字符串
     * @return 验真码
     */
    public String verifyCode(String token) {
        String normalized = extractToken(token);
        if (!TOKEN_PATTERN.matcher(normalized).matches()) {
            throw invalidProof();
        }
        String signature = normalized.split("\\.")[2];
        int length = Math.min(properties.qr().verifyCodeLength(), signature.length());
        return signature.substring(0, length);
    }

    /**
     * 常量时间比对验真码。
     *
     * @param token    令牌字符串
     * @param provided 用户输入的验真码
     * @return 是否匹配
     */
    public boolean matchesVerifyCode(String token, String provided) {
        if (provided == null || provided.isBlank()) {
            return false;
        }
        try {
            return cryptoService.constantTimeEquals(verifyCode(token), provided.trim());
        } catch (BusinessException exception) {
            return false;
        }
    }

    /**
     * 由令牌拼装完整验真链接（写入 {@code prescription.qr_code}）。
     *
     * @param token 令牌字符串
     * @return 验真链接
     */
    public String linkOf(String token) {
        String link = String.format(properties.qr().verifyUrlTemplate(), token);
        if (link.length() > MAX_LINK_LENGTH) {
            throw new IllegalStateException("qr link exceeds 1024 characters");
        }
        return link;
    }

    /**
     * 从扫码 / 粘贴内容中提取裸令牌（兼容完整 URL 与裸令牌两种形态）。
     *
     * @param qrContent 扫码内容
     * @return 裸令牌
     */
    public String extractToken(String qrContent) {
        if (qrContent == null) {
            throw invalidProof();
        }
        String content = qrContent.trim();
        int marker = content.indexOf("?t=");
        if (marker >= 0) {
            content = content.substring(marker + 3);
        }
        int separator = content.indexOf('&');
        if (separator >= 0) {
            content = content.substring(0, separator);
        }
        if (content.isBlank()) {
            throw invalidProof();
        }
        return content;
    }

    /**
     * 凭证摘要（完整 64 位十六进制），用于 {@code prescription_dispense_record.proof_digest}。
     *
     * @param proof 凭证原文
     * @return SHA-256 十六进制
     */
    public String proofDigest(String proof) {
        return digest(proof);
    }

    /**
     * 凭证摘要前 16 位十六进制，用于审计事件（进一步降低可逆风险）。
     *
     * @param proof 凭证原文
     * @return 前 16 位十六进制
     */
    public String auditDigest(String proof) {
        return digest(proof).substring(0, AUDIT_DIGEST_LENGTH);
    }

    private int graceDays() {
        return properties.qr() == null ? 0 : properties.qr().tokenGraceDays();
    }

    private byte[] encodePayload(TokenPayload payload) {
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException exception) {
            LOGGER.error("Failed to serialize qr token payload", exception);
            throw new IllegalStateException("qr token payload serialization failed", exception);
        }
    }

    private TokenPayload decodePayload(byte[] ciphertext) {
        try {
            return objectMapper.readValue(cryptoService.decrypt(ciphertext), TokenPayload.class);
        } catch (IOException | IllegalStateException | IllegalArgumentException exception) {
            // GCM tag 校验失败 / 载荷不可解析，统一视为凭证无效
            throw invalidProof();
        }
    }

    private String ensureLength(String token) {
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalStateException("qr token exceeds 700 characters");
        }
        return token;
    }

    private byte[] decode(String segment) {
        try {
            return URL_DECODER.decode(segment);
        } catch (IllegalArgumentException exception) {
            throw invalidProof();
        }
    }

    private static String digest(String proof) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    sha256.digest(String.valueOf(proof).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static BusinessException invalidProof() {
        return new BusinessException(ErrorCode.INVALID_VERIFICATION_PROOF);
    }

    /**
     * 令牌载荷（字段顺序固定 {@code v,pid,iat,exp}，禁止放入任何患者身份明文）。
     *
     * @param v   令牌版本
     * @param pid 处方主键
     * @param iat 签发时间（epoch 秒）
     * @param exp 失效时间（epoch 秒）
     */
    record TokenPayload(int v, long pid, long iat, long exp) {
    }
}
