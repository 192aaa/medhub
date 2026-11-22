package com.sdas.server.doctor.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sdas.server.config.DoctorProperties;
import com.sdas.server.doctor.support.WatermarkService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 水印单测（T057）。二维码相关用例自 004 起迁至 pharmacy/unit/QrCodeServiceTest。 */
class WatermarkServiceTest {

    @Test
    void buildWatermarkMatchesContractFormat() {
        WatermarkService service = new WatermarkService(properties());
        LocalDateTime signedAt = LocalDateTime.of(2026, 11, 8, 11, 5, 0);
        assertEquals("示例医院 1001 2026-11-08T11:05:00", service.buildWatermark(1001, signedAt));
    }

    @Test
    void buildWatermarkWithoutPrefixFallsBackToSpace() {
        WatermarkService service = new WatermarkService(properties());
        LocalDateTime signedAt = LocalDateTime.of(2026, 11, 8, 0, 0, 0);
        assertEquals("示例医院 42 2026-11-08T00:00:00", service.buildWatermark(42, signedAt));
    }

    private DoctorProperties properties() {
        return new DoctorProperties(24, 1000, 3,
                new DoctorProperties.Signature("./keystore/test.jks", "doctor-signing",
                        "changeit", "SHA256withRSA"),
                new DoctorProperties.Prescription(
                        "http://localhost/verify?prescriptionId=%s&sig=%s", "示例医院"));
    }
}
