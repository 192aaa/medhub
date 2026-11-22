package com.sdas.server.doctor.support;

import com.sdas.server.config.HospitalProperties;
import com.sdas.server.healthconsultation.support.KnowledgeHit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 治疗决策提示词构造（plan.md M2/M6）。
 *
 * <p>安全边界：固定约束只出现在 system prompt 中；患者对话与问诊内容只拼进 user 消息，
 * 避免「忽略以上指令」类注入改写系统规则。模型输出必须再次经
 * {@link DecisionPayloadParser} 白名单校验，提示词本身不作为安全保证。</p>
 */
@Component
public class DecisionPromptBuilder {

    private static final String JSON_SKELETON = String.join("\n",
            "{",
            "  \"summary\": {",
            "    \"chiefComplaint\": \"主诉一句话\",",
            "    \"presentIllness\": \"现病史，无内容填「暂无」\",",
            "    \"dialogHighlights\": [\"对话要点\"],",
            "    \"riskAlerts\": [\"风险提示，可为空数组\"]",
            "  },",
            "  \"suggestions\": [",
            "    {",
            "      \"diagnosis\": \"疑似××，禁止确定性措辞\",",
            "      \"likelihood\": \"HIGH|MEDIUM|LOW\",",
            "      \"basis\": [\"依据，至少 1 条\"],",
            "      \"workup\": [\"建议检查，可为空数组\"],",
            "      \"riskAlert\": \"风险提示或 null\"",
            "    }",
            "  ],",
            "  \"medications\": [",
            "    {",
            "      \"drugName\": \"药品通用名\",",
            "      \"dosage\": \"用法用量\",",
            "      \"caution\": \"注意事项\"",
            "    }",
            "  ]",
            "}");

    private final HospitalProperties hospitalProperties;

    /**
     * 构造提示词构造器。
     *
     * @param hospitalProperties 医院结构化信息
     */
    public DecisionPromptBuilder(HospitalProperties hospitalProperties) {
        this.hospitalProperties = hospitalProperties;
    }

    /**
     * 系统提示词（固定内容 + 医院信息）。
     *
     * @return 系统提示词
     */
    public String systemPrompt() {
        return String.join("\n",
                "你是医院辅助决策助手，为接诊医生整理本次问诊的结构化摘要与参考意见。",
                "规则：",
                "1. 只输出一个 JSON 对象，不要 Markdown 围栏、不要解释性文字。",
                "2. 不得输出确定性诊断措辞（如「确诊」「一定是」「百分之百」「必然」「可以排除」），"
                        + "一律使用「疑似」等不确定性表述。",
                "3. 用药只写通用名 + 用法用量 + 注意事项，不得输出 drugId、不得推荐目录外药品。",
                "4. 只引用本次问诊单与对话中的资料，不得虚构检查结果或既往病史。",
                "5. 医院信息：" + hospitalName() + "。",
                "输出骨架：",
                JSON_SKELETON);
    }

    /**
     * 用户提示词（问诊上下文）。
     *
     * @param context 上下文
     * @return 用户提示词
     */
    public String build(DecisionContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("【问诊单】").append(System.lineSeparator())
                .append("主诉：").append(nullToDash(context.chiefComplaint()))
                .append(System.lineSeparator())
                .append("描述：").append(nullToDash(context.description()))
                .append(System.lineSeparator());
        builder.append("【对话】").append(System.lineSeparator());
        if (context.dialog().isEmpty()) {
            builder.append("（暂无医患对话）").append(System.lineSeparator());
        } else {
            for (ChatTurn turn : context.dialog()) {
                builder.append("user".equals(turn.role()) ? "患者：" : "医生：")
                        .append(turn.content())
                        .append(System.lineSeparator());
            }
        }
        if (context.imageCount() > 0) {
            builder.append("【图片】含 ").append(context.imageCount())
                    .append(" 张图片，未提供文字描述，请在风险提示中说明依据有限。")
                    .append(System.lineSeparator());
        }
        appendKnowledge(builder, context.knowledge());
        if (context.evidenceLimited()) {
            builder.append("【依据判定】对话信息较少，摘要与建议须据实描述，不得补充未经提供的内容。")
                    .append(System.lineSeparator());
        }
        builder.append("请按系统提示的 JSON 骨架输出。");
        return builder.toString();
    }

    private static void appendKnowledge(StringBuilder builder, List<KnowledgeHit> knowledge) {
        if (knowledge.isEmpty()) {
            builder.append("【知识依据】（未检索到同科室知识，依据有限）").append(System.lineSeparator());
            return;
        }
        builder.append("【知识依据】").append(System.lineSeparator());
        for (int i = 0; i < knowledge.size(); i++) {
            builder.append(i + 1).append(". ").append(knowledge.get(i).diseaseName())
                    .append(System.lineSeparator());
        }
    }

    private String hospitalName() {
        String name = hospitalProperties == null ? null : hospitalProperties.name();
        return name == null || name.isBlank() ? "本院" : name;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "暂无" : value;
    }
}
