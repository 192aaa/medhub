package com.sdas.server.appointment.dto;

import java.math.BigDecimal;

/** 医生列表项响应（契约端点 2）。 */
public record DoctorResponse(Long doctorId, String name, String title, Long departmentId,
                             String hospital, Integer yearsOfExperience, String goodAt,
                             BigDecimal consultationFee, BigDecimal rating, int consultationCount,
                             String onlineStatus, String avatarUrl) {
}
