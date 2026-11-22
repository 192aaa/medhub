package com.sdas.server.doctor.model.converter;

import com.sdas.server.doctor.model.DegradedReason;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** 降级原因显式转换器，未知值降级为 NONE。 */
public final class DegradedReasonConverter implements SafeEnumConverter<DegradedReason> {

    @Override
    public Class<DegradedReason> enumType() {
        return DegradedReason.class;
    }

    @Override
    public DegradedReason defaultValue() {
        return DegradedReason.NONE;
    }
}
