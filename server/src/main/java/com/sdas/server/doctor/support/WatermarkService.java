package com.sdas.server.doctor.support;

import com.sdas.server.config.DoctorProperties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/**
 * 处方水印服务。
 *
 * <p>水印内容为「医院名 + 处方单号 + 生成时间」，患者端可见。</p>
 */
@Service
public class WatermarkService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DoctorProperties properties;

    /**
     * 构造水印服务。
     *
     * @param properties 医生接诊配置
     */
    public WatermarkService(DoctorProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成处方水印文本。
     *
     * @param prescriptionId 处方单号
     * @param signedAt       签名时间
     * @return 水印文本
     */
    public String buildWatermark(long prescriptionId, LocalDateTime signedAt) {
        return properties.prescription().watermarkPrefix() + " " + prescriptionId + " "
                + signedAt.format(FORMATTER);
    }
}
