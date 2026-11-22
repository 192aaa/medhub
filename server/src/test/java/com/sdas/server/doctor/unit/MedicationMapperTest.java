package com.sdas.server.doctor.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sdas.server.doctor.entity.DrugCatalog;
import com.sdas.server.doctor.support.MapResult;
import com.sdas.server.doctor.support.MedicationMapper;
import com.sdas.server.doctor.support.RawMedication;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 药品映射：精确/前缀匹配、目录外丢弃、需补齐标注、条数截断。 */
class MedicationMapperTest {

    private final MedicationMapper mapper = new MedicationMapper();

    @Test
    @DisplayName("精确匹配取目录规格")
    void exactMatch() {
        MapResult result = mapper.map(
                List.of(new RawMedication("阿莫西林胶囊", "口服，一次 2 粒", "青霉素过敏者禁用")),
                List.of(drug(901L, "阿莫西林胶囊", "0.25g*20")));

        assertThat(result.drafts()).hasSize(1);
        assertThat(result.drafts().get(0).drugId()).isEqualTo(901L);
        assertThat(result.drafts().get(0).spec()).isEqualTo("0.25g*20");
        assertThat(result.drafts().get(0).needsCompletion()).isFalse();
        assertThat(result.ignoredMedications()).isEmpty();
    }

    @Test
    @DisplayName("前缀最长匹配：带规格后缀的模型名落到目录药品")
    void prefixMatch() {
        List<DrugCatalog> catalog = List.of(
                drug(901L, "阿莫西林胶囊", "0.25g*20"),
                drug(902L, "阿莫西林胶囊颗粒", "0.125g*12"));

        MapResult result = mapper.map(
                List.of(new RawMedication("阿莫西林胶囊颗粒 0.125g", "一次 2 袋", null)), catalog);

        assertThat(result.drafts()).hasSize(1);
        assertThat(result.drafts().get(0).drugId()).isEqualTo(902L);
    }

    @Test
    @DisplayName("目录外药品被丢弃并计入 ignoredMedications")
    void dropsUnknownDrug() {
        MapResult result = mapper.map(
                List.of(new RawMedication("目录外药品", "一次 1 片", null)),
                List.of(drug(901L, "阿莫西林胶囊", "0.25g*20")));

        assertThat(result.drafts()).isEmpty();
        assertThat(result.ignoredMedications()).containsExactly("目录外药品");
    }

    @Test
    @DisplayName("用法用量为空时需医生补齐")
    void marksNeedsCompletion() {
        MapResult result = mapper.map(List.of(new RawMedication("阿莫西林胶囊", "", null)),
                List.of(drug(901L, "阿莫西林胶囊", "0.25g*20")));

        assertThat(result.drafts().get(0).needsCompletion()).isTrue();
        assertThat(result.drafts().get(0).dosage()).isNull();
    }

    @Test
    @DisplayName("日频次超过 6 次标注剂量需核对")
    void flagsDangerousFrequency() {
        MapResult result = mapper.map(
                List.of(new RawMedication("阿莫西林胶囊", "口服，一日 8 次，一次 1 粒", null)),
                List.of(drug(901L, "阿莫西林胶囊", "0.25g*20")));

        assertThat(result.drafts().get(0).needsCompletion()).isTrue();
        assertThat(result.drafts().get(0).caution()).contains("剂量需医生核对");
    }

    @Test
    @DisplayName("超过 10 条草稿截断")
    void truncatesToTen() {
        List<RawMedication> raw = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new RawMedication("药品" + index, "一次 1 片", null))
                .toList();
        List<DrugCatalog> catalog = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> drug(900L + index, "药品" + index, "10mg*10"))
                .toList();

        MapResult result = mapper.map(raw, catalog);

        assertThat(result.drafts()).hasSize(10);
    }

    private static DrugCatalog drug(long id, String name, String spec) {
        return new DrugCatalog(id, name, spec, "示例药厂", "胶囊", "盒", true, true, "PRESET",
                null, null, 0);
    }
}
