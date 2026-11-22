package com.sdas.server.doctor.support;

/**
 * 对话轮次（上下文组装结果，仅内存使用）。
 *
 * @param role    角色（{@code user} 患者 / {@code assistant} 医生）
 * @param content 内容
 */
public record ChatTurn(String role, String content) {
}
