package com.sdas.server.healthconsultation.dto;

/**
 * SSE 事件载荷（字段与 specs/005-ai-health-consultation/contracts/ai-health-consultation-api.md 逐字一致）。
 *
 * <p>事件顺序固定：{@code accepted} →（紧急时 {@code emergency}）→ {@code delta}* →
 * {@code disclaimer} → {@code done} 或 {@code error}（互斥）。</p>
 */
public final class HealthSseEvents {

    private HealthSseEvents() {
    }

    /**
     * 患者消息已落库。
     *
     * @param messageId 患者消息主键
     * @param sessionId 会话主键
     */
    public record Accepted(long messageId, long sessionId) {
    }

    /**
     * 挂号引导预填信息。
     *
     * @param departmentId   科室主键
     * @param departmentName 科室名称
     */
    public record Booking(long departmentId, String departmentName) {
    }

    /**
     * 紧急预警（必须先于任何 delta）。
     *
     * @param level    级别
     * @param title    标题
     * @param advice   就医指引
     * @param ruleCode 命中规则码（模型独立判定时为 MODEL_ONLY）
     * @param booking  挂号预填（科室反查失败时为 null）
     */
    public record Emergency(String level, String title, String advice, String ruleCode,
                            Booking booking) {
    }

    /**
     * 正文增量。
     *
     * @param text 增量文本
     */
    public record Delta(String text) {
    }

    /**
     * 免责声明（文案取自服务端常量）。
     *
     * @param text 声明文案
     */
    public record Disclaimer(String text) {
    }

    /**
     * 正常结束。
     *
     * @param messageId 助手消息主键
     * @param truncated 病历上下文是否被截断
     * @param modelUsed 实际模型名
     */
    public record Done(long messageId, boolean truncated, String modelUsed) {
    }

    /**
     * 失败结束（与 done 互斥）。
     *
     * @param code      业务码
     * @param message   提示信息（取自 ErrorCode）
     * @param retryable 是否可重试
     */
    public record Error(int code, String message, boolean retryable) {
    }
}
