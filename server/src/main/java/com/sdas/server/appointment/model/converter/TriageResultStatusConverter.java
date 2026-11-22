package com.sdas.server.appointment.model.converter;

import com.sdas.server.appointment.model.TriageResultStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** TriageResultStatus 显式转换器，未知值降级为 SUCCESS。 */
public final class TriageResultStatusConverter implements SafeEnumConverter<TriageResultStatus> {

    @Override
    public Class<TriageResultStatus> enumType() {
        return TriageResultStatus.class;
    }

    @Override
    public TriageResultStatus defaultValue() {
        return TriageResultStatus.SUCCESS;
    }
}
