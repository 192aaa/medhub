package com.sdas.server.healthconsultation.support;

import java.util.List;

/**
 * 医学知识检索命中项。
 *
 * <p>来源：specs/005-ai-health-consultation/plan.md M3。{@code diseaseName} 在本期承载
 * 「一句话要点」（该表无康复/用药专用列，已登记缺口）；{@code kbVersion} 供 prompt 标注依据版本。</p>
 *
 * @param diseaseName     知识点（一句话要点）
 * @param departmentId    归属科室
 * @param kbVersion       知识库版本
 * @param matchedKeywords 命中的检索词
 */
public record KnowledgeHit(
        String diseaseName,
        long departmentId,
        String kbVersion,
        List<String> matchedKeywords) {
}
