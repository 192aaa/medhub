package com.sdas.server.appointment.dto;

import java.util.List;

/** AI 智能导诊响应（契约端点 4）。 */
public record TriageResponse(Long triageSessionId, String status, List<Long> recommendedDepartmentIds,
                            List<TriageDepartment> recommendedDepartments,
                            List<TriageDepartment> fallbackDepartments, String modelUsed,
                            long elapsedMs) {
}
