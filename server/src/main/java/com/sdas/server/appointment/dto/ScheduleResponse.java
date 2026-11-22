package com.sdas.server.appointment.dto;

import java.time.LocalDate;

/** 医生排班项响应（契约端点 3）。 */
public record ScheduleResponse(Long scheduleId, LocalDate workDate, String period, int slotsTotal,
                               int slotsBooked) {
}
