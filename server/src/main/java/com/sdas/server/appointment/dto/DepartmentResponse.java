package com.sdas.server.appointment.dto;

/** 科室响应（契约端点 1）。 */
public record DepartmentResponse(Long departmentId, String name, String description, String iconUrl,
                                 int sortOrder, boolean enabled) {
}
