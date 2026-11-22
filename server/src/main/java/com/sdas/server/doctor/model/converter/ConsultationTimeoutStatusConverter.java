package com.sdas.server.doctor.model.converter;

import com.sdas.server.doctor.model.ConsultationTimeoutStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** ConsultationTimeoutStatus 显式转换器，未知值降级为 NONE。 */
public final class ConsultationTimeoutStatusConverter implements SafeEnumConverter<ConsultationTimeoutStatus> {

    @Override
    public Class<ConsultationTimeoutStatus> enumType() {
        return ConsultationTimeoutStatus.class;
    }

    @Override
    public ConsultationTimeoutStatus defaultValue() {
        return ConsultationTimeoutStatus.NONE;
    }
}
