package com.sdas.server.appointment.service;

import java.util.List;

/**
 * AI 导诊原始结果（内部传输对象）。
 *
 * @param success          调用是否成功（含超时/异常视为失败）
 * @param departmentNames  AI 推荐的科室名称列表
 */
public record TriageAiResult(boolean success, List<String> departmentNames) {

    /** 防御性拷贝：科室列表以不可变副本保存，避免调用方回写内部状态。 */
    public TriageAiResult {
        departmentNames = departmentNames == null ? List.of() : List.copyOf(departmentNames);
    }

    /** @return 超时/异常降级结果 */
    public static TriageAiResult timeout() {
        return new TriageAiResult(false, List.of());
    }
}
