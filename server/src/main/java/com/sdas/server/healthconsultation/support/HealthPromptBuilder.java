package com.sdas.server.healthconsultation.support;

import com.sdas.server.config.HealthProperties;
import com.sdas.server.config.HospitalProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Prompt 组装（M1 / M2 / M3 / M5 / M6 的汇合点）。
 *
 * <p>system prompt 固定包含：角色与安全边界（不得诊断、不得处方加减量，FR-015）、
 * {@code EMERGENCY: YES|NO} 首行协议、病历摘要段、知识依据段、医院信息段与免责要求。
 * 总长按 {@code context-max-chars} 裁剪：先移除最不相关的知识条目，再截断病历摘要；
 * 对话窗口超预算时从最旧轮次开始丢弃（最近优先）。</p>
 */
@Component
public class HealthPromptBuilder {

    private static final String ROLE_AND_PROTOCOL = """
            你是医院面向患者的健康咨询助手，只能提供健康科普与就医建议，不得给出诊断结论或处方调整。
            回答结构：
            第 1 行必须且只能是 EMERGENCY: YES 或 EMERGENCY: NO（依据是否出现需要立即就医的症状判断）；
            第 2 行起为给患者的正文，使用中文，简洁分点，不重复本段说明。
            正文中禁止出现「确诊」「处方加减量」等表述；涉及用药须引导复诊或咨询开方医生。
            不要在正文中自行添加免责声明，服务端会统一附加。
            """;

    private static final String LIMITED_EVIDENCE =
            "若病历摘要或知识依据为空、或标注为「依据有限」，必须在回答中明确说明依据有限，禁止编造来源。";

    private final MedicalContextAssembler medicalContextAssembler;

    private final KnowledgeRetriever knowledgeRetriever;

    private final HealthProperties properties;

    private final HospitalProperties hospitalProperties;

    /**
     * 构造 Prompt 组装器。
     *
     * @param medicalContextAssembler 病历上下文组装器
     * @param knowledgeRetriever      知识检索器
     * @param properties              005 配置
     * @param hospitalProperties      医院信息配置
     */
    public HealthPromptBuilder(MedicalContextAssembler medicalContextAssembler,
                               KnowledgeRetriever knowledgeRetriever,
                               HealthProperties properties,
                               HospitalProperties hospitalProperties) {
        this.medicalContextAssembler = medicalContextAssembler;
        this.knowledgeRetriever = knowledgeRetriever;
        this.properties = properties;
        this.hospitalProperties = hospitalProperties;
    }

    /**
     * 组装本轮模型调用入参。
     *
     * @param patientId   患者主键（来自认证上下文）
     * @param window      热窗口（时间正序，可为空）
     * @param patientText 本轮患者输入
     * @return Prompt 与截断标记
     */
    public PromptResult build(long patientId, List<ChatTurn> window, String patientText) {
        MedicalContext medical = medicalContextAssembler.assemble(patientId);
        List<KnowledgeHit> hits = new ArrayList<>(
                knowledgeRetriever.retrieve(patientText, properties.knowledgeTopK()));

        int budget = properties.contextMaxChars();
        String head = ROLE_AND_PROTOCOL + System.lineSeparator()
                + "【医院信息】" + System.lineSeparator()
                + "名称：" + nullSafe(hospitalProperties.name()) + "；地址："
                + nullSafe(hospitalProperties.address()) + "；咨询电话："
                + nullSafe(hospitalProperties.phone()) + "；急救电话："
                + nullSafe(hospitalProperties.emergencyPhone()) + "；门诊时间："
                + nullSafe(hospitalProperties.serviceHours()) + System.lineSeparator()
                + LIMITED_EVIDENCE + System.lineSeparator();

        String medicalText = medical.summaryText();
        String knowledgeText = renderKnowledge(hits);

        while (!hits.isEmpty() && head.length() + medicalText.length() + knowledgeText.length()
                > budget) {
            hits.remove(hits.size() - 1);
            knowledgeText = renderKnowledge(hits);
        }
        if (head.length() + medicalText.length() + knowledgeText.length() > budget) {
            int allowed = Math.max(0, budget - head.length() - knowledgeText.length() - 8);
            if (medicalText.length() > allowed) {
                medicalText = allowed > 0 ? medicalText.substring(0, allowed) + "…" : "（依据有限）";
            }
        }

        String systemPrompt = head
                + "【病历摘要】" + System.lineSeparator() + medicalText + System.lineSeparator()
                + "【知识依据】" + System.lineSeparator() + knowledgeText + System.lineSeparator();

        List<ChatTurn> turns = new ArrayList<>(window);
        turns.add(new ChatTurn(ChatTurn.ROLE_USER, patientText));
        turns = trimTurns(turns, Math.max(0, budget - systemPrompt.length()));

        HealthChatCommand command = new HealthChatCommand(systemPrompt, turns,
                properties.maxOutputTokens(), properties.model());
        return new PromptResult(command, medical.truncated());
    }

    private static String renderKnowledge(List<KnowledgeHit> hits) {
        if (hits.isEmpty()) {
            return "（本次未命中知识条目，回答须说明依据有限）";
        }
        return hits.stream()
                .map(hit -> "- " + hit.diseaseName()
                        + (hit.kbVersion() == null ? "" : "（kb " + hit.kbVersion() + "）"))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static List<ChatTurn> trimTurns(List<ChatTurn> turns, int budget) {
        List<ChatTurn> result = new ArrayList<>(turns);
        int total = result.stream().mapToInt(turn -> turn.content().length()).sum();
        while (total > budget && result.size() > 1) {
            ChatTurn removed = result.remove(0);
            total -= removed.content().length();
        }
        return result;
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
