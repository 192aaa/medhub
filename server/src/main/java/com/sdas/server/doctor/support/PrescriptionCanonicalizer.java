package com.sdas.server.doctor.support;

import com.sdas.server.doctor.entity.Prescription;
import com.sdas.server.doctor.entity.PrescriptionItem;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方内容签名原文规范化（003 定义，004 复用）。
 *
 * <p>格式：{@code prescriptionId|doctorId|consultationId|drugId:spec:dosage:caution;...|createdAt}。
 * 开方时签名与验真时复验必须使用同一实现，否则「内容签名复验」永远失败——因此从
 * {@code PrescriptionServiceImpl} 抽为共享工具（004 T036 依赖）。</p>
 *
 * <p>重要：{@code createdAt} 必须落库后再参与签名（服务端截断到秒并显式写入
 * {@code prescription.created_at}），否则数据库默认值与签名时刻不一致会导致复验失败。</p>
 */
public final class PrescriptionCanonicalizer {

    private PrescriptionCanonicalizer() {
    }

    /**
     * 按明细构造签名原文。
     *
     * @param prescriptionId 处方主键
     * @param doctorId       医生主键
     * @param consultationId 问诊单主键
     * @param items          明细（规格为最终落库值）
     * @param createdAt      落库创建时间（精确到秒）
     * @return 规范化原文
     */
    public static String canonicalize(long prescriptionId, long doctorId, long consultationId,
                                      List<PrescriptionItem> items, LocalDateTime createdAt) {
        StringBuilder builder = new StringBuilder();
        builder.append(prescriptionId).append('|').append(doctorId).append('|')
                .append(consultationId).append('|');
        for (int i = 0; i < items.size(); i++) {
            PrescriptionItem item = items.get(i);
            builder.append(item.drugId()).append(':').append(item.spec()).append(':')
                    .append(item.dosage()).append(':').append(item.caution());
            if (i < items.size() - 1) {
                builder.append(';');
            }
        }
        builder.append('|').append(createdAt);
        return builder.toString();
    }

    /**
     * 从已落库处方与明细构造签名原文（验真时复验用）。
     *
     * @param prescription 处方
     * @param items        处方明细
     * @return 规范化原文
     */
    public static String canonicalize(Prescription prescription, List<PrescriptionItem> items) {
        return canonicalize(prescription.id(), prescription.doctorId(),
                prescription.consultationId(), items, prescription.createdAt());
    }
}
