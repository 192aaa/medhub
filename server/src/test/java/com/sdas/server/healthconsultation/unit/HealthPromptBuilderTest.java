package com.sdas.server.healthconsultation.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.config.HealthProperties;
import com.sdas.server.config.HospitalProperties;
import com.sdas.server.healthconsultation.support.ChatTurn;
import com.sdas.server.healthconsultation.support.HealthPromptBuilder;
import com.sdas.server.healthconsultation.support.KnowledgeHit;
import com.sdas.server.healthconsultation.support.KnowledgeRetriever;
import com.sdas.server.healthconsultation.support.MedicalContext;
import com.sdas.server.healthconsultation.support.MedicalContextAssembler;
import com.sdas.server.healthconsultation.support.PromptResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prompt 组装：安全边界、首行协议、依据有限与预算裁剪。 */
class HealthPromptBuilderTest {

    private final MedicalContextAssembler assembler = mock(MedicalContextAssembler.class);

    private final KnowledgeRetriever retriever = mock(KnowledgeRetriever.class);

    private final HealthProperties properties =
            new HealthProperties("deepseek-v4-flash", 8000, 3000, 1024, 6000, 1500, 12, 2000, 200, 5);

    private final HospitalProperties hospital = new HospitalProperties("示例医院", "示例路 1 号",
            "0000-00000000", "120", "08:00-17:30");

    private final HealthPromptBuilder builder =
            new HealthPromptBuilder(assembler, retriever, properties, hospital);

    @Test
    @DisplayName("system prompt 含首行协议、处方边界与免责要求")
    void promptContract() {
        when(assembler.assemble(anyLong())).thenReturn(
                new MedicalContext("【最近就诊记录】1) 2026-11-01 内科 张*｜主诉：咳嗽三天",
                        List.of(11L), false));
        when(retriever.retrieve(anyString(), anyInt())).thenReturn(List.of(
                new KnowledgeHit("咳嗽：保持湿度", 1L, "2026.11", List.of("咳嗽"))));

        PromptResult result = builder.build(7L, List.of(new ChatTurn("user", "上一轮问题")),
                "我咳嗽有痰");

        String system = result.command().systemPrompt();
        assertThat(system).contains("EMERGENCY: YES");
        assertThat(system).contains("不得给出诊断结论或处方调整");
        assertThat(system).contains("示例医院");
        assertThat(system).contains("咳嗽三天");
        assertThat(system).contains("kb 2026.11");
        assertThat(system.length()).isLessThanOrEqualTo(properties.contextMaxChars());
        assertThat(result.command().turns()).last()
                .extracting(ChatTurn::content).isEqualTo("我咳嗽有痰");
        assertThat(result.contextTruncated()).isFalse();
    }

    @Test
    @DisplayName("知识空命中时要求声明依据有限")
    void emptyKnowledgeAsksLimitedEvidence() {
        when(assembler.assemble(anyLong())).thenReturn(
                new MedicalContext("（该患者暂无历史就诊记录）", List.of(), false));
        when(retriever.retrieve(anyString(), anyInt())).thenReturn(List.of());

        PromptResult result = builder.build(7L, List.of(), "随便问问");

        assertThat(result.command().systemPrompt()).contains("依据有限");
    }

    @Test
    @DisplayName("窗口超预算时从最旧轮次丢弃")
    void trimsOldestTurns() {
        when(assembler.assemble(anyLong())).thenReturn(
                new MedicalContext("摘要", List.of(), false));
        when(retriever.retrieve(anyString(), anyInt())).thenReturn(List.of());
        String longText = "很长的历史内容".repeat(1000);

        PromptResult result = builder.build(7L,
                List.of(new ChatTurn("user", longText), new ChatTurn("assistant", longText)),
                "本轮问题");

        assertThat(result.command().turns().size()).isLessThan(3);
        assertThat(result.command().turns()).last()
                .extracting(ChatTurn::content).isEqualTo("本轮问题");
    }
}
