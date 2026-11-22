package com.sdas.server.appointment.model.converter;

import com.sdas.server.appointment.model.DoctorOnlineStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** DoctorOnlineStatus 显式转换器，未知值降级为 OFFLINE。 */
public final class DoctorOnlineStatusConverter implements SafeEnumConverter<DoctorOnlineStatus> {

    @Override
    public Class<DoctorOnlineStatus> enumType() {
        return DoctorOnlineStatus.class;
    }

    @Override
    public DoctorOnlineStatus defaultValue() {
        return DoctorOnlineStatus.OFFLINE;
    }
}
