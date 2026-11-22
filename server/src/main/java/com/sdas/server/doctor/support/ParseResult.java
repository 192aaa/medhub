package com.sdas.server.doctor.support;

import com.sdas.server.doctor.model.DegradedReason;

/**
 * 解析结果（失败只带原因，不回传原始文本）。
 *
 * @param success 是否解析成功
 * @param decision 解析内容（失败为 null）
 * @param reason   失败原因（成功为 {@code NONE}）
 */
public record ParseResult(boolean success, ParsedDecision decision, DegradedReason reason) {

    /**
     * 构造成功结果。
     *
     * @param decision 解析内容
     * @return 成功结果
     */
    public static ParseResult success(ParsedDecision decision) {
        return new ParseResult(true, decision, DegradedReason.NONE);
    }

    /**
     * 构造失败结果。
     *
     * @param reason 失败原因
     * @return 失败结果
     */
    public static ParseResult failed(DegradedReason reason) {
        return new ParseResult(false, null, reason);
    }
}
