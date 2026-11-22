package com.sdas.server.healthconsultation.support;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型首行协议解析（M5）。
 *
 * <p>协议：回答第 1 行必须且只能是 {@code EMERGENCY: YES} 或 {@code EMERGENCY: NO}。
 * 首行在服务端解析并从正文中剥离（不展示给患者）；缺失或格式非法按
 * {@link ModelEmergencySignal#UNAVAILABLE} 处理（安全优先 → 规则命中即预警）。</p>
 */
public final class EmergencyHeaderParser {

    private static final Pattern HEADER =
            Pattern.compile("^EMERGENCY\\s*[:：]\\s*(YES|NO)\\b", Pattern.CASE_INSENSITIVE);

    /** 首块缓冲上限：超过此长度仍无换行则视为无协议头。 */
    public static final int MAX_HEADER_BUFFER = 64;

    private EmergencyHeaderParser() {
    }

    /**
     * 解析首个内容块。
     *
     * @param head 首个内容块（可能含首行 + 正文）
     * @return 信号与剥离首行后的正文
     */
    public static ParsedAnswer parse(String head) {
        String normalized = head == null ? "" : head.stripLeading();
        Matcher matcher = HEADER.matcher(normalized);
        if (!matcher.find()) {
            return new ParsedAnswer(ModelEmergencySignal.UNAVAILABLE, normalized);
        }
        ModelEmergencySignal signal = "YES".equals(matcher.group(1).toUpperCase(Locale.ROOT))
                ? ModelEmergencySignal.YES
                : ModelEmergencySignal.NO;
        String body = normalized.substring(matcher.end());
        if (body.startsWith("\r\n")) {
            body = body.substring(2);
        } else if (body.startsWith("\n")) {
            body = body.substring(1);
        }
        return new ParsedAnswer(signal, body.stripLeading());
    }

    /** 模型首行信号。 */
    public enum ModelEmergencySignal {

        /** 模型判定需要立即就医。 */
        YES,

        /** 模型判定非紧急。 */
        NO,

        /** 首行缺失/非法或模型不可用。 */
        UNAVAILABLE
    }

    /**
     * 解析结果。
     *
     * @param signal 首行信号
     * @param body   剥离首行后的正文
     */
    public record ParsedAnswer(ModelEmergencySignal signal, String body) {
    }
}
