package com.sdas.server.doctor.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdas.server.config.TreatmentDecisionProperties;
import com.sdas.server.doctor.model.DegradedReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 模型输出解析与字段级校验（plan.md M1/M6）。
 *
 * <p>处理围栏剥离、括号截取、Jackson 反序列化与白名单校验；同时执行
 * <b>确定性措辞归一化</b>（FR-007/SC-003）：命中「确诊/一定是/百分之百/必然/可以排除」
 * 时去词并前置「疑似」，去词后过短的条目直接丢弃——不依赖提示词自觉。
 * 解析失败只返回 {@link DegradedReason#PARSE_FAILED}，不回传原始文本。</p>
 */
@Component
public class DecisionPayloadParser {

    /** 确定性措辞黑名单。 */
    private static final Set<String> DETERMINISTIC_WORDS =
            Set.of("确诊", "一定是", "百分之百", "必然", "可以排除");

    private static final String SUSPECT_PREFIX = "疑似";

    private static final int DIAGNOSIS_MIN_LENGTH = 2;

    private static final int MAX_COMPLAINT_LENGTH = 200;

    private static final int MAX_TEXT_LENGTH = 1000;

    private static final int MAX_ITEM_LENGTH = 200;

    private static final int MAX_LIST_ITEMS = 8;

    private static final Set<String> LIKELIHOODS = Set.of("HIGH", "MEDIUM", "LOW");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TreatmentDecisionProperties properties;

    /**
     * 构造解析器。
     *
     * @param properties 006 配置（建议条数上限）
     */
    public DecisionPayloadParser(TreatmentDecisionProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析模型输出。
     *
     * @param rawJson 模型原始输出
     * @return 解析结果（失败只带原因）
     */
    public ParseResult parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return ParseResult.failed(DegradedReason.EMPTY);
        }
        JsonNode root = readRoot(rawJson);
        if (root == null) {
            return ParseResult.failed(DegradedReason.PARSE_FAILED);
        }
        JsonNode summaryNode = root.get("summary");
        if (summaryNode == null || !summaryNode.isObject()) {
            return ParseResult.failed(DegradedReason.PARSE_FAILED);
        }
        String chiefComplaint = text(summaryNode, "chiefComplaint");
        if (chiefComplaint == null) {
            return ParseResult.failed(DegradedReason.PARSE_FAILED);
        }
        DecisionSummary summary = new DecisionSummary(
                truncate(chiefComplaint, MAX_COMPLAINT_LENGTH),
                truncate(nullToEmpty(text(summaryNode, "presentIllness")), MAX_TEXT_LENGTH),
                texts(summaryNode.get("dialogHighlights")),
                texts(summaryNode.get("riskAlerts")));
        ParsedDecision decision = new ParsedDecision(summary,
                parseSuggestions(root.get("suggestions")),
                parseMedications(root.get("medications")));
        return ParseResult.success(decision);
    }

    private static JsonNode readRoot(String rawJson) {
        String cleaned = rawJson.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(cleaned.substring(start, end + 1));
            return root != null && root.isObject() ? root : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private List<DiagnosisSuggestion> parseSuggestions(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DiagnosisSuggestion> suggestions = new ArrayList<>();
        for (JsonNode item : node) {
            if (suggestions.size() >= properties.maxSuggestions()) {
                break;
            }
            DiagnosisSuggestion suggestion = parseSuggestion(item);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }
        return List.copyOf(suggestions);
    }

    private static DiagnosisSuggestion parseSuggestion(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String diagnosis = normalizeDiagnosis(text(item, "diagnosis"));
        if (diagnosis == null) {
            return null;
        }
        List<String> basis = texts(item.get("basis"));
        if (basis.isEmpty()) {
            return null;
        }
        return new DiagnosisSuggestion(diagnosis, likelihood(text(item, "likelihood")), basis,
                texts(item.get("workup")),
                blankToNull(truncate(nullToEmpty(text(item, "riskAlert")), MAX_ITEM_LENGTH)));
    }

    private static List<RawMedication> parseMedications(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<RawMedication> medications = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            String drugName = text(item, "drugName");
            if (drugName == null) {
                continue;
            }
            medications.add(new RawMedication(truncate(drugName, MAX_ITEM_LENGTH),
                    truncate(nullToEmpty(text(item, "dosage")), MAX_ITEM_LENGTH),
                    truncate(nullToEmpty(text(item, "caution")), MAX_ITEM_LENGTH)));
        }
        return List.copyOf(medications);
    }

    private static String normalizeDiagnosis(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw;
        boolean hit = false;
        for (String word : DETERMINISTIC_WORDS) {
            if (cleaned.contains(word)) {
                cleaned = cleaned.replace(word, "");
                hit = true;
            }
        }
        cleaned = truncate(cleaned, MAX_ITEM_LENGTH);
        if (!hit) {
            return cleaned;
        }
        if (cleaned.length() < DIAGNOSIS_MIN_LENGTH) {
            return null;
        }
        return cleaned.startsWith(SUSPECT_PREFIX) ? cleaned : SUSPECT_PREFIX + cleaned;
    }

    private static String likelihood(String raw) {
        if (raw == null) {
            return "LOW";
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return LIKELIHOODS.contains(normalized) ? normalized : "LOW";
    }

    private static List<String> texts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (values.size() >= MAX_LIST_ITEMS) {
                break;
            }
            String value = item == null || item.isNull() ? null : item.asText();
            if (value != null && !value.isBlank()) {
                values.add(truncate(value, MAX_ITEM_LENGTH));
            }
        }
        return List.copyOf(values);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isArray() || value.isObject()) {
            return null;
        }
        String raw = value.asText();
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
