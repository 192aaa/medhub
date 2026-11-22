package com.sdas.server.healthconsultation.support;

import java.util.List;

/**
 * 病历上下文聚合结果（M2）。
 *
 * <p>仅用于服务端 prompt 组装：<b>不回传前端、不写日志、不进 Redis</b>。</p>
 *
 * @param summaryText               摘要文本（已按字符预算裁剪）
 * @param referencedConsultationIds 实际纳入摘要的问诊单主键（可追溯，不复制病历全文）
 * @param truncated                 是否因预算被截断
 */
public record MedicalContext(
        String summaryText,
        List<Long> referencedConsultationIds,
        boolean truncated) {
}
