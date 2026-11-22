package com.sdas.server.common.log;

import java.util.regex.Pattern;

/**
 * 日志脱敏工具。
 *
 * <p>宪法原则 IV：审计日志禁止记录密码明文、验证码明文、完整手机号/身份证号、API Key。</p>
 */
public final class LogMasker {

    private static final Pattern SECRET_PAIR = Pattern.compile(
            "(?i)(password|passwd|pwd|token|secret|api[-_]?key|verifycode|code)\\s*[=:]\\s*[^&\\s,;]+");

    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private LogMasker() {
    }

    /**
     * 对可能包含敏感信息的文本脱敏。
     *
     * @param raw 原始文本，可为 null
     * @return 脱敏后的文本
     */
    public static String mask(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String masked = SECRET_PAIR.matcher(raw).replaceAll("$1=***");
        masked = ID_CARD.matcher(masked).replaceAll("****");
        return PHONE.matcher(masked).replaceAll("****");
    }
}
