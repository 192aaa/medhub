package com.sdas.server.doctor.model.converter;

import com.sdas.server.doctor.model.DoctorAccountStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** DoctorAccountStatus 显式转换器，未知值降级为 ACTIVE。 */
public final class DoctorAccountStatusConverter implements SafeEnumConverter<DoctorAccountStatus> {

    @Override
    public Class<DoctorAccountStatus> enumType() {
        return DoctorAccountStatus.class;
    }

    @Override
    public DoctorAccountStatus defaultValue() {
        return DoctorAccountStatus.ACTIVE;
    }
}
