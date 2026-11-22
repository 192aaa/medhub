package com.sdas.server.healthconsultation.support;

import java.util.List;

/**
 * 模型调用入参（M1）。
 *
 * @param systemPrompt    角色 + 安全边界 + 免责要求 + 首行 emergency 协议
 * @param turns           历史窗口 + 本轮患者消息（时间正序）
 * @param maxOutputTokens 单轮最大输出 token
 * @param model           模型名
 */
public record HealthChatCommand(
        String systemPrompt,
        List<ChatTurn> turns,
        int maxOutputTokens,
        String model) {
}
