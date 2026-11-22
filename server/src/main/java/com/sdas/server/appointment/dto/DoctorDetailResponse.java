package com.sdas.server.appointment.dto;

import java.math.BigDecimal;
import java.util.List;

/** 医生详情响应（契约端点 3），含可约排班。 */
public record DoctorDetailResponse(Long doctorId, String name, String title, Long departmentId,
                                  String hospital, Integer yearsOfExperience, String goodAt,
                                  BigDecimal consultationFee, BigDecimal rating, int consultationCount,
                                  String onlineStatus, String avatarUrl, List<ScheduleResponse> schedule) {
}
