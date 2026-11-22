package com.sdas.server.healthconsultation.support;

import com.sdas.server.healthconsultation.model.EmergencyVerdict;
import com.sdas.server.healthconsultation.support.EmergencyHeaderParser.ModelEmergencySignal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 紧急判定结论派生（M5 判定矩阵，纯函数）。
 *
 * <table>
 *   <caption>矩阵</caption>
 *   <tr><th>规则命中</th><th>模型首行</th><th>结论</th><th>warned</th></tr>
 *   <tr><td>是</td><td>YES</td><td>CONFIRMED</td><td>true</td></tr>
 *   <tr><td>是</td><td>NO</td><td>REJECTED</td><td>false</td></tr>
 *   <tr><td>是</td><td>UNAVAILABLE</td><td>UNAVAILABLE</td><td>true（安全优先）</td></tr>
 *   <tr><td>否</td><td>YES</td><td>MODEL_ONLY</td><td>true</td></tr>
 *   <tr><td>否</td><td>NO / UNAVAILABLE</td><td>不产生记录</td><td>false</td></tr>
 * </table>
 */
@Component
public class EmergencyDecisionResolver {

    /** 模型独立判定时写入留痕的规则码。 */
    public static final String MODEL_ONLY_RULE_CODE = "MODEL_ONLY";

    /**
     * 派生判定结论。
     *
     * @param ruleCode 命中的规则码；未命中时为 {@code null}
     * @param signal   模型首行信号
     * @return 判定结果；规则未命中且模型未判紧急时为空（不写留痕，避免表膨胀）
     */
    public Optional<Result> resolve(String ruleCode, ModelEmergencySignal signal) {
        boolean ruleHit = ruleCode != null && !ruleCode.isBlank();
        if (ruleHit) {
            return switch (signal) {
                case YES -> Optional.of(new Result(EmergencyVerdict.CONFIRMED, true, ruleCode));
                case NO -> Optional.of(new Result(EmergencyVerdict.REJECTED, false, ruleCode));
                case UNAVAILABLE ->
                        Optional.of(new Result(EmergencyVerdict.UNAVAILABLE, true, ruleCode));
            };
        }
        if (signal == ModelEmergencySignal.YES) {
            return Optional.of(new Result(EmergencyVerdict.MODEL_ONLY, true,
                    MODEL_ONLY_RULE_CODE));
        }
        return Optional.empty();
    }

    /**
     * 判定结果。
     *
     * @param verdict  结论
     * @param warned   是否最终预警
     * @param ruleCode 留痕规则码
     */
    public record Result(EmergencyVerdict verdict, boolean warned, String ruleCode) {
    }
}
