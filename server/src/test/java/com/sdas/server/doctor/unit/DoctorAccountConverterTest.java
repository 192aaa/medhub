package com.sdas.server.doctor.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sdas.server.doctor.model.DoctorAccountStatus;
import com.sdas.server.doctor.model.converter.DoctorAccountStatusConverter;
import org.junit.jupiter.api.Test;

/** DoctorAccountStatus 显式转换器单测（T021）：往返一致、未知值降级。 */
class DoctorAccountConverterTest {

    private final DoctorAccountStatusConverter converter = new DoctorAccountStatusConverter();

    @Test
    void convertRoundTripIsStable() {
        assertEquals("ACTIVE", converter.convertToDatabaseColumn(DoctorAccountStatus.ACTIVE));
        assertEquals(DoctorAccountStatus.ACTIVE, converter.convertToEntityAttribute("ACTIVE"));
        assertEquals("DISABLED", converter.convertToDatabaseColumn(DoctorAccountStatus.DISABLED));
        assertEquals(DoctorAccountStatus.DISABLED, converter.convertToEntityAttribute("DISABLED"));
    }

    @Test
    void unknownValueFallsBackToDefault() {
        assertEquals(DoctorAccountStatus.ACTIVE, converter.convertToEntityAttribute("UNKNOWN"));
        assertEquals(DoctorAccountStatus.ACTIVE, converter.convertToEntityAttribute(null));
        assertEquals(DoctorAccountStatus.ACTIVE, converter.convertToEntityAttribute(""));
    }
}
