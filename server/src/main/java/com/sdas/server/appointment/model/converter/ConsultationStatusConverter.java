package com.sdas.server.appointment.model.converter;

import com.sdas.server.appointment.model.ConsultationStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** ConsultationStatus 显式转换器，未知值降级为 PENDING。 */
public final class ConsultationStatusConverter implements SafeEnumConverter<ConsultationStatus> {

    @Override
    public Class<ConsultationStatus> enumType() {
        return ConsultationStatus.class;
    }

    @Override
    public ConsultationStatus defaultValue() {
        return ConsultationStatus.PENDING;
    }
}
