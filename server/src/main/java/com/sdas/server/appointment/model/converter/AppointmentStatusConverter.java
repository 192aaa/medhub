package com.sdas.server.appointment.model.converter;

import com.sdas.server.appointment.model.AppointmentStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** AppointmentStatus 显式转换器，未知值降级为 PENDING_PAYMENT。 */
public final class AppointmentStatusConverter implements SafeEnumConverter<AppointmentStatus> {

    @Override
    public Class<AppointmentStatus> enumType() {
        return AppointmentStatus.class;
    }

    @Override
    public AppointmentStatus defaultValue() {
        return AppointmentStatus.PENDING_PAYMENT;
    }
}
