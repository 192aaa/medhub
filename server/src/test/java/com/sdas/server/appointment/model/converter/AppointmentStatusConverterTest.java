package com.sdas.server.appointment.model.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sdas.server.appointment.model.AppointmentStatus;
import org.junit.jupiter.api.Test;

/** 枚举转换器降级语义单测（tasks.md T010 / T022 示例）。 */
class AppointmentStatusConverterTest {

    private final AppointmentStatusConverter converter = new AppointmentStatusConverter();

    @Test
    void toDatabaseColumnUsesName() {
        assertEquals("PAID", converter.convertToDatabaseColumn(AppointmentStatus.PAID));
        assertEquals("PENDING_PAYMENT", converter.convertToDatabaseColumn(null));
    }

    @Test
    void unknownValueFallsBackToDefault() {
        assertEquals(AppointmentStatus.PENDING_PAYMENT, converter.convertToEntityAttribute("NONSENSE"));
        assertEquals(AppointmentStatus.PENDING_PAYMENT, converter.convertToEntityAttribute(null));
        assertEquals(AppointmentStatus.PAID, converter.convertToEntityAttribute("PAID"));
    }
}
