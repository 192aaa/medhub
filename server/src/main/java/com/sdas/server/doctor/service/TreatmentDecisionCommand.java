package com.sdas.server.doctor.service;

/**
 * 治疗决策模型调用入参（plan.md M1）。
 *
 * <p>Spring AI 类型不得出现在本签名及其返回值中。</p>
 *
 * @param systemPrompt    系统提示词（安全边界，固定内容）
 * @param userPrompt      用户提示词（脱敏后的问诊上下文）
 * @param maxOutputTokens 输出 token 上限
 * @param model           模型名
 */
public record TreatmentDecisionCommand(
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        String model) {
}
