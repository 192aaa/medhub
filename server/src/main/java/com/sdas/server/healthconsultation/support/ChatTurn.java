package com.sdas.server.healthconsultation.support;

/**
 * 单轮对话消息。
 *
 * @param role    角色：{@code user} / {@code assistant}
 * @param content 内容
 */
public record ChatTurn(String role, String content) {

    /** 患者角色常量。 */
    public static final String ROLE_USER = "user";

    /** 助手角色常量。 */
    public static final String ROLE_ASSISTANT = "assistant";

    /**
     * 是否为助手轮次。
     *
     * @return true 表示助手
     */
    public boolean assistant() {
        return ROLE_ASSISTANT.equals(role);
    }
}
