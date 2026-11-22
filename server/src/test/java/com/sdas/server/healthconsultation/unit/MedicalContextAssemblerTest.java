package com.sdas.server.healthconsultation.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sdas.server.appointment.entity.Consultation;
import com.sdas.server.appointment.entity.Department;
import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.appointment.repository.ConsultationRepository;
import com.sdas.server.appointment.repository.DepartmentRepository;
import com.sdas.server.config.HealthProperties;
import com.sdas.server.doctor.entity.DrugCatalog;
import com.sdas.server.doctor.entity.Prescription;
import com.sdas.server.doctor.entity.PrescriptionItem;
import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.doctor.model.converter.ConsultationTimeoutStatusConverter;
import com.sdas.server.doctor.repository.DrugCatalogRepository;
import com.sdas.server.doctor.repository.PrescriptionItemRepository;
import com.sdas.server.doctor.repository.PrescriptionRepository;
import com.sdas.server.healthconsultation.repository.DoctorNameRepository;
import com.sdas.server.healthconsultation.support.MedicalContext;
import com.sdas.server.healthconsultation.support.MedicalContextAssembler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 病历上下文聚合（预算、截断、无记录）。 */
class MedicalContextAssemblerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 9, 0);

    private final ConsultationRepository consultationRepository = mock(ConsultationRepository.class);

    private final PrescriptionRepository prescriptionRepository = mock(PrescriptionRepository.class);

    private final PrescriptionItemRepository itemRepository = mock(PrescriptionItemRepository.class);

    private final DrugCatalogRepository drugRepository = mock(DrugCatalogRepository.class);

    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

    private final DoctorNameRepository doctorNameRepository = mock(DoctorNameRepository.class);

    private final MedicalContextAssembler assembler = new MedicalContextAssembler(
            consultationRepository, prescriptionRepository, itemRepository, drugRepository,
            departmentRepository, doctorNameRepository, properties(1500));

    @Test
    @DisplayName("无就诊记录返回固定文案且不查下游表")
    void emptyHistory() {
        when(consultationRepository.findRecentCompletedByPatientId(anyLong(), any(), anyInt()))
                .thenReturn(List.of());

        MedicalContext context = assembler.assemble(7L);

        assertThat(context.summaryText()).isEqualTo("（该患者暂无历史就诊记录）");
        assertThat(context.truncated()).isFalse();
        assertThat(context.referencedConsultationIds()).isEmpty();
        verify(consultationRepository).findRecentCompletedByPatientId(eq(7L),
                eq(ConsultationStatus.COMPLETED), eq(3));
    }

    @Test
    @DisplayName("聚合主诉、科室、医生与处方明细")
    void assembleSummary() {
        stubOneRecord();
        MedicalContext context = assembler.assemble(7L);

        assertThat(context.summaryText())
                .contains("内科")
                .contains("张*")
                .contains("咳嗽三天")
                .contains("阿莫西林胶囊")
                .contains("青霉素过敏者禁用");
        assertThat(context.truncated()).isFalse();
        assertThat(context.referencedConsultationIds()).containsExactly(11L);
    }

    @Test
    @DisplayName("超预算时退化为最近一次主诉并置 truncated")
    void truncatesOverBudget() {
        stubOneRecord();
        MedicalContextAssembler tiny = new MedicalContextAssembler(
                consultationRepository, prescriptionRepository, itemRepository, drugRepository,
                departmentRepository, doctorNameRepository, properties(40));

        MedicalContext context = tiny.assemble(7L);

        assertThat(context.truncated()).isTrue();
        assertThat(context.summaryText()).contains("最近一次主诉");
        assertThat(context.referencedConsultationIds()).containsExactly(11L);
    }

    private void stubOneRecord() {
        Consultation consultation = new Consultation(11L, 21L, 7L, 31L, 41L, "咳嗽三天", null,
                null, ConsultationStatus.COMPLETED, null, null,
                new ConsultationTimeoutStatusConverter().defaultValue(), null, null,
                NOW, NOW, 0);
        when(consultationRepository.findRecentCompletedByPatientId(anyLong(), any(), anyInt()))
                .thenReturn(List.of(consultation));

        Prescription prescription = new Prescription(51L, 11L, 31L, 7L, PrescriptionStatus.SIGNED,
                "sig", "qr", "wm", NOW, NOW.plusDays(3), NOW, NOW, 0);
        when(prescriptionRepository.findByConsultationIdInAndStatus(anyList(),
                eq(PrescriptionStatus.SIGNED))).thenReturn(List.of(prescription));

        PrescriptionItem item = new PrescriptionItem(61L, 51L, 71L, "0.25g*20",
                "口服，一次2粒，一日3次", "青霉素过敏者禁用", NOW);
        when(itemRepository.findByPrescriptionIdIn(anyList())).thenReturn(List.of(item));

        DrugCatalog drug = new DrugCatalog(71L, "阿莫西林胶囊", "0.25g*20", "厂商", "胶囊", "盒",
                true, true, "PRESET", NOW, NOW, 0);
        when(drugRepository.findByIdsIn(anyList())).thenReturn(List.of(drug));

        Department department = new Department(41L, "内科", null, null, 1, true, NOW, NOW);
        when(departmentRepository.findByIdsIn(anyList())).thenReturn(List.of(department));

        when(doctorNameRepository.findNamesByIds(anyList())).thenReturn(Map.of(31L, "张*"));
    }

    private static HealthProperties properties(int medicalMaxChars) {
        return new HealthProperties("deepseek-v4-flash", 8000, 3000, 1024, 6000,
                medicalMaxChars, 12, 2000, 200, 5);
    }
}
