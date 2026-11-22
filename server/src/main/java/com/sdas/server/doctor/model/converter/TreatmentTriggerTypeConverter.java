package com.sdas.server.doctor.model.converter;

import com.sdas.server.doctor.model.TreatmentTriggerType;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** 触发方式显式转换器，未知值降级为 AUTO。 */
public final class TreatmentTriggerTypeConverter
        implements SafeEnumConverter<TreatmentTriggerType> {

    @Override
    public Class<TreatmentTriggerType> enumType() {
        return TreatmentTriggerType.class;
    }

    @Override
    public TreatmentTriggerType defaultValue() {
        return TreatmentTriggerType.AUTO;
    }
}
