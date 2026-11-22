package com.sdas.server.healthconsultation.support;

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
import com.sdas.server.doctor.repository.DrugCatalogRepository;
import com.sdas.server.doctor.repository.PrescriptionItemRepository;
import com.sdas.server.doctor.repository.PrescriptionRepository;
import com.sdas.server.healthconsultation.repository.DoctorNameRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 病历上下文只读聚合（M2 / R-04）。
 *
 * <p>取最近 3 次已完成问诊 + 对应已签名处方与明细，生成带字符预算的纯文本摘要；
 * 超预算按「最近优先」截断并置 {@code truncated}。无记录时返回固定的「暂无历史就诊记录」文案，
 * 由 prompt 要求模型声明依据有限（US1 场景 2）。</p>
 *
 * <p>隐私：摘要仅供 prompt 使用，不写日志、不回传前端、不入 Redis。</p>
 */
@Component
public class MedicalContextAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(MedicalContextAssembler.class);

    private static final String EMPTY_SUMMARY = "（该患者暂无历史就诊记录）";

    private static final int MAX_CONSULTATIONS = 3;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ConsultationRepository consultationRepository;

    private final PrescriptionRepository prescriptionRepository;

    private final PrescriptionItemRepository prescriptionItemRepository;

    private final DrugCatalogRepository drugCatalogRepository;

    private final DepartmentRepository departmentRepository;

    private final DoctorNameRepository doctorNameRepository;

    private final HealthProperties properties;

    /**
     * 构造病历上下文组装器。
     *
     * @param consultationRepository     问诊单只读仓储
     * @param prescriptionRepository     处方只读仓储
     * @param prescriptionItemRepository 处方明细只读仓储
     * @param drugCatalogRepository      药品目录只读仓储
     * @param departmentRepository       科室只读仓储
     * @param doctorNameRepository       医生姓名只读仓储
     * @param properties                 005 配置
     */
    public MedicalContextAssembler(ConsultationRepository consultationRepository,
                                   PrescriptionRepository prescriptionRepository,
                                   PrescriptionItemRepository prescriptionItemRepository,
                                   DrugCatalogRepository drugCatalogRepository,
                                   DepartmentRepository departmentRepository,
                                   DoctorNameRepository doctorNameRepository,
                                   HealthProperties properties) {
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.drugCatalogRepository = drugCatalogRepository;
        this.departmentRepository = departmentRepository;
        this.doctorNameRepository = doctorNameRepository;
        this.properties = properties;
    }

    /**
     * 组装患者病历摘要。
     *
     * @param patientId 患者主键（必须来自认证上下文，禁止由请求参数传入）
     * @return 摘要文本、引用问诊单与截断标记
     */
    public MedicalContext assemble(long patientId) {
        List<Consultation> consultations = consultationRepository
                .findRecentCompletedByPatientId(patientId, ConsultationStatus.COMPLETED,
                        MAX_CONSULTATIONS);
        if (consultations.isEmpty()) {
            return new MedicalContext(EMPTY_SUMMARY, List.of(), false);
        }

        List<Long> consultationIds = consultations.stream().map(Consultation::id).toList();
        List<Prescription> prescriptions = prescriptionRepository
                .findByConsultationIdInAndStatus(consultationIds, PrescriptionStatus.SIGNED);

        List<Long> prescriptionIds = prescriptions.stream().map(Prescription::id).toList();
        List<PrescriptionItem> items = prescriptionItemRepository
                .findByPrescriptionIdIn(prescriptionIds);
        List<Long> drugIds = items.stream().map(PrescriptionItem::drugId).distinct().toList();
        Map<Long, DrugCatalog> drugs = drugCatalogRepository.findByIdsIn(drugIds).stream()
                .collect(Collectors.toMap(DrugCatalog::id, Function.identity(), (a, b) -> a));

        List<Long> departmentIds = consultations.stream().map(Consultation::departmentId)
                .distinct().toList();
        Map<Long, String> departments = departmentRepository.findByIdsIn(departmentIds).stream()
                .collect(Collectors.toMap(Department::id, Department::name, (a, b) -> a));

        List<Long> doctorIds = consultations.stream().map(Consultation::doctorId)
                .distinct().toList();
        Map<Long, String> doctors = doctorNameRepository.findNamesByIds(doctorIds);

        Map<Long, List<PrescriptionItem>> itemsByPrescription = items.stream()
                .collect(Collectors.groupingBy(PrescriptionItem::prescriptionId,
                        HashMap::new, Collectors.toList()));
        Map<Long, Prescription> prescriptionByConsultation = prescriptions.stream()
                .collect(Collectors.toMap(Prescription::consultationId, Function.identity(),
                        (a, b) -> a));

        int budget = properties.contextMedicalMaxChars();
        StringBuilder sb = new StringBuilder("【最近就诊记录】（仅供本次咨询参考）");
        List<Long> referenced = new ArrayList<>(consultations.size());
        boolean truncated = false;

        for (int i = 0; i < consultations.size(); i++) {
            Consultation consultation = consultations.get(i);
            String entry = renderEntry(i + 1, consultation, prescriptionByConsultation,
                    itemsByPrescription, drugs, departments, doctors);
            if (sb.length() + entry.length() > budget) {
                truncated = true;
                if (referenced.isEmpty()) {
                    sb.append(System.lineSeparator())
                            .append(renderFallback(consultation, prescriptionByConsultation,
                                    itemsByPrescription, drugs));
                    referenced.add(consultation.id());
                }
                break;
            }
            sb.append(System.lineSeparator()).append(entry);
            referenced.add(consultation.id());
        }

        LOG.debug("medical context assembled, patientId={}, consultations={}, truncated={}",
                patientId, referenced.size(), truncated);
        return new MedicalContext(sb.toString(), List.copyOf(referenced), truncated);
    }

    private String renderEntry(int index, Consultation consultation,
                               Map<Long, Prescription> prescriptionByConsultation,
                               Map<Long, List<PrescriptionItem>> itemsByPrescription,
                               Map<Long, DrugCatalog> drugs,
                               Map<Long, String> departments,
                               Map<Long, String> doctors) {
        StringBuilder entry = new StringBuilder();
        entry.append(index).append(") ")
                .append(consultation.createdAt() == null ? "-"
                        : consultation.createdAt().format(DATE))
                .append(' ')
                .append(departments.getOrDefault(consultation.departmentId(), "未知科室"))
                .append(' ')
                .append(doctors.getOrDefault(consultation.doctorId(), "-"))
                .append("｜主诉：")
                .append(nullToDash(consultation.complaint()));

        Prescription prescription = prescriptionByConsultation.get(consultation.id());
        if (prescription != null) {
            List<PrescriptionItem> items = itemsByPrescription
                    .getOrDefault(prescription.id(), List.of());
            for (PrescriptionItem item : items) {
                DrugCatalog drug = drugs.get(item.drugId());
                String name = drug == null ? "药品#" + item.drugId() : drug.name();
                entry.append(System.lineSeparator()).append("   处方：").append(name);
                if (drug != null && drug.spec() != null) {
                    entry.append(' ').append(drug.spec());
                } else if (item.spec() != null) {
                    entry.append(' ').append(item.spec());
                }
                entry.append("｜用法：").append(nullToDash(item.dosage()));
                if (item.caution() != null && !item.caution().isBlank()) {
                    entry.append("｜注意：").append(item.caution());
                }
            }
        }
        return entry.toString();
    }

    private String renderFallback(Consultation consultation,
                                  Map<Long, Prescription> prescriptionByConsultation,
                                  Map<Long, List<PrescriptionItem>> itemsByPrescription,
                                  Map<Long, DrugCatalog> drugs) {
        StringBuilder sb = new StringBuilder("1) 最近一次主诉：");
        sb.append(nullToDash(consultation.complaint()));
        Prescription prescription = prescriptionByConsultation.get(consultation.id());
        if (prescription != null) {
            String drugNames = itemsByPrescription.getOrDefault(prescription.id(), List.of())
                    .stream()
                    .map(item -> {
                        DrugCatalog drug = drugs.get(item.drugId());
                        return drug == null ? null : drug.name();
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining("、"));
            if (!drugNames.isBlank()) {
                sb.append("｜处方药：").append(drugNames);
            }
        }
        return sb.toString();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
