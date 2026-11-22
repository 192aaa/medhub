package com.sdas.server.doctor.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.doctor.model.MessageType;
import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.doctor.model.converter.ConsultationTimeoutStatusConverter;
import com.sdas.server.doctor.model.converter.MessageTypeConverter;
import com.sdas.server.doctor.model.converter.PrescriptionStatusConverter;
import org.junit.jupiter.api.Test;

/** 003 其余 3 个枚举转换器单测（T021）：往返一致、未知值降级。 */
class EnumConverterTest {

    @Test
    void prescriptionStatusRoundTripAndFallback() {
        PrescriptionStatusConverter converter = new PrescriptionStatusConverter();
        assertEquals("SIGNED", converter.convertToDatabaseColumn(PrescriptionStatus.SIGNED));
        assertEquals(PrescriptionStatus.SIGNED, converter.convertToEntityAttribute("SIGNED"));
        assertEquals(PrescriptionStatus.DRAFT, converter.convertToEntityAttribute("UNKNOWN"));
        assertEquals(PrescriptionStatus.DRAFT, converter.convertToEntityAttribute(null));
    }

    @Test
    void messageTypeRoundTripAndFallback() {
        MessageTypeConverter converter = new MessageTypeConverter();
        assertEquals("DOCTOR", converter.convertToDatabaseColumn(MessageType.DOCTOR));
        assertEquals(MessageType.DOCTOR, converter.convertToEntityAttribute("DOCTOR"));
        assertEquals(MessageType.SYSTEM, converter.convertToEntityAttribute("UNKNOWN"));
    }

    @Test
    void timeoutStatusRoundTripAndFallback() {
        ConsultationTimeoutStatusConverter converter = new ConsultationTimeoutStatusConverter();
        assertEquals("ADMIN_NOTIFIED",
                converter.convertToDatabaseColumn(ConsultationTimeoutStatus.ADMIN_NOTIFIED));
        assertEquals(ConsultationTimeoutStatus.ADMIN_NOTIFIED,
                converter.convertToEntityAttribute("ADMIN_NOTIFIED"));
        assertEquals(ConsultationTimeoutStatus.NONE, converter.convertToEntityAttribute("UNKNOWN"));
        assertEquals(ConsultationTimeoutStatus.NONE, converter.convertToEntityAttribute(null));
    }
}
