package com.sdas.server.doctor.support;

import java.util.List;

/**
 * 药品映射结果。
 *
 * @param drafts             命中目录的草稿
 * @param ignoredMedications 目录外被丢弃的药名
 */
public record MapResult(List<MedicationDraft> drafts, List<String> ignoredMedications) {
}
