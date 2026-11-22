package com.sdas.server.doctor.support;

import com.sdas.server.doctor.entity.DrugCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 用药草稿映射（plan.md M5 / R-07）。
 *
 * <p>模型只输出通用名，服务端在启用药品目录内做「归一化精确匹配 → 前缀最长匹配」，
 * 规格取目录值；目录外药品丢弃并回传 {@code ignoredMedications}，不向前端暴露非法药品。
 * 剂量疑似异常（单次剂量 &gt; 目录规格数值 × 10 或日频次 &gt; 6）只标注、不阻断。</p>
 */
@Component
public class MedicationMapper {

    /** 草稿条数上限（与 003 处方明细上限一致）。 */
    private static final int MAX_MEDICATIONS = 10;

    private static final double DOSE_MULTIPLIER = 10.0;

    private static final int MAX_DAILY_FREQUENCY = 6;

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private static final Pattern FREQUENCY =
            Pattern.compile("(?:一日|每日|每天)\\s*(\\d+)\\s*次");

    private static final String DOSE_WARNING = "剂量需医生核对";

    /**
     * 映射草稿。
     *
     * @param raw     模型输出的原始用药
     * @param catalog 启用药品目录（可为空）
     * @return 草稿与目录外药名
     */
    public MapResult map(List<RawMedication> raw, List<DrugCatalog> catalog) {
        if (raw == null || raw.isEmpty()) {
            return new MapResult(List.of(), List.of());
        }
        List<DrugCatalog> drugs = catalog == null ? List.of() : catalog;
        Map<String, DrugCatalog> exact = new HashMap<>();
        for (DrugCatalog drug : drugs) {
            exact.putIfAbsent(normalize(drug.name()), drug);
        }
        List<MedicationDraft> drafts = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        for (RawMedication item : raw) {
            if (drafts.size() >= MAX_MEDICATIONS) {
                break;
            }
            DrugCatalog drug = match(normalize(item.drugName()), exact, drugs);
            if (drug == null) {
                ignored.add(item.drugName());
            } else {
                drafts.add(draft(drug, item));
            }
        }
        return new MapResult(List.copyOf(drafts), List.copyOf(ignored));
    }

    private static DrugCatalog match(String normalizedName, Map<String, DrugCatalog> exact,
                                     List<DrugCatalog> catalog) {
        if (normalizedName.isEmpty()) {
            return null;
        }
        DrugCatalog hit = exact.get(normalizedName);
        if (hit != null) {
            return hit;
        }
        DrugCatalog best = null;
        int bestLength = 0;
        for (DrugCatalog drug : catalog) {
            String catalogName = normalize(drug.name());
            if (!catalogName.isEmpty() && normalizedName.startsWith(catalogName)
                    && catalogName.length() > bestLength) {
                best = drug;
                bestLength = catalogName.length();
            }
        }
        return best;
    }

    private static MedicationDraft draft(DrugCatalog drug, RawMedication item) {
        String dosage = item.dosage() == null ? "" : item.dosage().trim();
        boolean needsCompletion = dosage.length() < 2;
        String caution = item.caution() == null || item.caution().isBlank()
                ? null : item.caution().trim();
        if (riskyDose(dosage, drug.spec())) {
            needsCompletion = true;
            caution = caution == null ? DOSE_WARNING : DOSE_WARNING + "；" + caution;
        }
        return new MedicationDraft(drug.id(), drug.name(), drug.spec(),
                dosage.isEmpty() ? null : dosage, caution, needsCompletion);
    }

    private static boolean riskyDose(String dosage, String spec) {
        if (dosage == null || dosage.isBlank()) {
            return false;
        }
        Matcher frequency = FREQUENCY.matcher(dosage);
        if (frequency.find() && Integer.parseInt(frequency.group(1)) > MAX_DAILY_FREQUENCY) {
            return true;
        }
        double specValue = firstNumber(spec);
        double doseValue = firstNumber(dosage);
        return specValue > 0 && doseValue > specValue * DOSE_MULTIPLIER;
    }

    private static double firstNumber(String value) {
        if (value == null) {
            return 0;
        }
        Matcher matcher = NUMBER.matcher(value);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[（(][^）)]*[）)]", "")
                .replaceAll("\\s+", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
