package com.sdas.server.doctor.support;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.config.PharmacyProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 处方二维码服务（zxing，唯一新增依赖）。
 *
 * <p>二维码内容为处方验真链接（004 起为 {@code v1} 令牌 URL），扫码可核对处方真伪。
 * 尺寸与纠错级别取自 {@code sdas.pharmacy.qr.image-size/error-correction}
 * （research.md R-09：令牌约 470 字符，需 480px + M 级）。</p>
 */
@Service
public class QrCodeService {

    private static final int QUIET_ZONE_MODULES = 2;

    private final DoctorProperties doctorProperties;

    private final PharmacyProperties pharmacyProperties;

    /**
     * 构造二维码服务。
     *
     * @param doctorProperties   医生接诊配置（验真链接模板）
     * @param pharmacyProperties 处方取药配置（图片尺寸与纠错级别）
     */
    public QrCodeService(DoctorProperties doctorProperties,
                         PharmacyProperties pharmacyProperties) {
        this.doctorProperties = doctorProperties;
        this.pharmacyProperties = pharmacyProperties;
    }

    /**
     * 生成处方验真链接（003 明文形态，保留以兼容既有调用与契约字段 {@code qrCode} 的拼装逻辑）。
     *
     * @param prescriptionId 处方主键
     * @param signatureShort 签名摘要（截断）
     * @return 验真链接
     */
    public String buildVerifyUrl(long prescriptionId, String signatureShort) {
        return String.format(doctorProperties.prescription().verifyUrlTemplate(),
                prescriptionId, signatureShort);
    }

    /**
     * 生成二维码 PNG 图片字节。
     *
     * @param content 二维码内容（令牌 URL）
     * @return PNG 字节
     */
    public byte[] generatePng(String content) {
        int size = pharmacyProperties.qr().imageSize();
        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION,
                ErrorCorrectionLevel.valueOf(pharmacyProperties.qr().errorCorrection()),
                EncodeHintType.MARGIN, QUIET_ZONE_MODULES);
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    content, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_SIGN_FAILED);
        }
    }
}
