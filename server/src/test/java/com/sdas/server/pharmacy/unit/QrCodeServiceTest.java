package com.sdas.server.pharmacy.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.sdas.server.common.BusinessException;
import com.sdas.server.config.DoctorProperties;
import com.sdas.server.config.PharmacyProperties;
import com.sdas.server.doctor.support.QrCodeService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** 二维码单测（T018）：480×480、可被 ZXing 解码回原文、超长内容抛业务异常。 */
class QrCodeServiceTest {

    @Test
    void generatePngIs480SquareAndDecodable() throws Exception {
        String content = tokenUrl();
        byte[] png = service().generatePng(content);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(480, image.getWidth());
        assertEquals(480, image.getHeight());
        assertEquals(content, decode(image));
    }

    @Test
    void generatePngRejectsOversizedContent() {
        assertThrows(BusinessException.class, () -> service().generatePng("x".repeat(5000)));
    }

    @Test
    void generatePngStaysUnder16Kb() {
        byte[] png = service().generatePng(tokenUrl());
        assertTrue(png.length < 16 * 1024, "二维码图片应 ≤ 16 KB，实际 " + png.length);
    }

    private String tokenUrl() {
        return "http://localhost:5183/pharmacy/verify?t=v1." + "A".repeat(120) + "."
                + "B".repeat(342);
    }

    private String decode(BufferedImage image) throws Exception {
        return new MultiFormatReader()
                .decode(new BinaryBitmap(new HybridBinarizer(
                        new BufferedImageLuminanceSource(image))))
                .getText();
    }

    private QrCodeService service() {
        return new QrCodeService(doctorProperties(), pharmacyProperties());
    }

    private DoctorProperties doctorProperties() {
        return new DoctorProperties(24, 1000, 3,
                new DoctorProperties.Signature("./keystore/test.jks", "doctor-signing",
                        "changeit", "SHA256withRSA"),
                new DoctorProperties.Prescription(
                        "http://localhost/verify?prescriptionId=%s&sig=%s", "示例医院"));
    }

    private PharmacyProperties pharmacyProperties() {
        return new PharmacyProperties(new PharmacyProperties.Qr(
                "http://localhost:5183/pharmacy/verify?t=%s", "v1", 12, 480, "M", 30));
    }
}
