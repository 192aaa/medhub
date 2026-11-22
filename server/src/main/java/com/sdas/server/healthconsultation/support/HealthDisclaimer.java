package com.sdas.server.healthconsultation.support;

/**
 * 医学免责声明唯一来源（R-08）。
 *
 * <p>SSE {@code disclaimer} 事件、{@code ai_health_message.disclaimer} 落库与前端渲染
 * 均引用本常量；禁止在其它位置硬编码或拼接文案。契约：{@code contracts/ai-health-consultation-api.md}。</p>
 */
public final class HealthDisclaimer {

    /** 固定免责声明文案（逐字与契约一致）。 */
    public static final String TEXT = "仅供参考，不构成医疗诊断";

    private HealthDisclaimer() {
    }
}
