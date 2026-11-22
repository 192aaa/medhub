package com.sdas.server.doctor.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.entity.ConsultationImage;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationImageRepository;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.config.TreatmentDecisionProperties;
import com.sdas.server.doctor.entity.ConsultationMessage;
import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.doctor.model.MessageType;
import com.sdas.server.doctor.repository.ConsultationMessageRepository;
import com.sdas.server.doctor.support.ChatTurn;
import com.sdas.server.doctor.support.DecisionContext;
import com.sdas.server.doctor.support.DecisionContextAssembler;
import com.sdas.server.healthconsultation.support.KnowledgeHit;
import com.sdas.server.healthconsultation.support.KnowledgeRetriever;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 上下文组装：40 条上限、字符预算最近优先、依据有限判定、知识同科室优先。 */
class DecisionContextAssemblerTest {

    private static final long CONSULTATION_ID = 501L;

    private static final long DEPARTMENT_ID = 7L;

    private ConsultationRepository consultationRepository;

    private ConsultationMessageRepository messageRepository;

    private ConsultationImageRepository imageRepository;

    private KnowledgeRetriever knowledgeRetriever;

    private DecisionContextAssembler assembler;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationRepository.class);
        messageRepository = mock(ConsultationMessageRepository.class);
        imageRepository = mock(ConsultationImageRepository.class);
        knowledgeRetriever = mock(KnowledgeRetriever.class);
        assembler = new DecisionContextAssembler(consultationRepository, messageRepository,
                imageRepository, knowledgeRetriever, properties());
        when(consultationRepository.findById(CONSULTATION_ID))
                .thenReturn(Optional.of(consultation()));
        when(imageRepository.findByConsultationId(CONSULTATION_ID)).thenReturn(List.of());
        when(knowledgeRetriever.retrieve(anyString(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("对话只取最近 40 条且保持正序")
    void keepsLastFortyMessages() {
        when(messageRepository.findByConsultationId(CONSULTATION_ID))
                .thenReturn(messages(50, 1));

        DecisionContext context = assembler.assemble(CONSULTATION_ID, DEPARTMENT_ID);

        assertThat(context.dialog()).hasSize(40);
        assertThat(context.dialog().get(0).content()).isEqualTo("内容10");
        assertThat(context.dialog().get(39).content()).isEqualTo("内容49");
    }

    @Test
    @DisplayName("超字符预算时按最近优先截断并保持正序")
    void truncatesByRecentFirst() {
        when(messageRepository.findByConsultationId(CONSULTATION_ID))
                .thenReturn(messages(40, 100));

        DecisionContext context = assembler.assemble(CONSULTATION_ID, DEPARTMENT_ID);

        assertThat(context.dialog()).hasSize(30);
        assertThat(context.dialog().get(context.dialog().size() - 1).content()).hasSize(100);
    }

    @Test
    @DisplayName("无对话时 evidenceLimited=true")
    void marksEvidenceLimitedWithoutDialog() {
        when(messageRepository.findByConsultationId(CONSULTATION_ID)).thenReturn(List.of());

        DecisionContext context = assembler.assemble(CONSULTATION_ID, DEPARTMENT_ID);

        assertThat(context.dialog()).isEmpty();
        assertThat(context.evidenceLimited()).isTrue();
    }

    @Test
    @DisplayName("图片数量进入上下文，摘要可标注无文字描述")
    void reportsImageCount() {
        when(messageRepository.findByConsultationId(CONSULTATION_ID)).thenReturn(List.of());
        when(imageRepository.findByConsultationId(CONSULTATION_ID)).thenReturn(List.of(image()));

        DecisionContext context = assembler.assemble(CONSULTATION_ID, DEPARTMENT_ID);

        assertThat(context.imageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("知识结果同科室优先、跨科室保留")
    void reordersKnowledge() {
        when(messageRepository.findByConsultationId(CONSULTATION_ID))
                .thenReturn(messages(3, 1));
        when(knowledgeRetriever.retrieve(anyString(), anyInt())).thenReturn(List.of(
                new KnowledgeHit("跨科室要点", 99L, "v1", List.of()),
                new KnowledgeHit("同科室要点", DEPARTMENT_ID, "v1", List.of())));

        DecisionContext context = assembler.assemble(CONSULTATION_ID, DEPARTMENT_ID);

        assertThat(context.knowledge()).extracting(KnowledgeHit::diseaseName)
                .containsExactly("同科室要点", "跨科室要点");
    }

    @Test
    @DisplayName("角色映射：患者为 user、医生为 assistant")
    void mapsRoles() {
        when(messageRepository.findByConsultationId(CONSULTATION_ID)).thenReturn(List.of(
                message(1L, MessageType.PATIENT, "咳嗽"),
                message(2L, MessageType.DOCTOR, "多久了")));

        DecisionContext context = assembler.assemble(CONSULTATION_ID, DEPARTMENT_ID);

        assertThat(context.dialog()).extracting(ChatTurn::role)
                .containsExactly("user", "assistant");
    }

    private static List<ConsultationMessage> messages(int count, int contentLength) {
        List<ConsultationMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String content = "内容" + i;
            StringBuilder builder = new StringBuilder(content);
            while (builder.length() < contentLength) {
                builder.append('x');
            }
            messages.add(message((long) i + 1, MessageType.PATIENT,
                    builder.substring(0, Math.max(contentLength, content.length()))));
        }
        return messages;
    }

    private static ConsultationMessage message(long id, MessageType type, String content) {
        return new ConsultationMessage(id, CONSULTATION_ID, type, 1L, content,
                LocalDateTime.now());
    }

    private static ConsultationImage image() {
        return new ConsultationImage(1L, CONSULTATION_ID, "/img/1.png", "image/png", 1024L,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static Consultation consultation() {
        return new Consultation(CONSULTATION_ID, 1L, 10001L, 101L, DEPARTMENT_ID,
                "咳嗽 3 天", "受凉后咳嗽", "摘要", ConsultationStatus.IN_PROGRESS, null, null,
                ConsultationTimeoutStatus.NONE, null, null, LocalDateTime.now(),
                LocalDateTime.now(), 0);
    }

    private static TreatmentDecisionProperties properties() {
        return new TreatmentDecisionProperties(null, 0, 0, 40, 3000, 5, 5, 10, 3, 30);
    }
}
