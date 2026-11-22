package com.sdas.server.appointment.model.converter;

import com.sdas.server.appointment.model.SchedulePeriod;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** SchedulePeriod 显式转换器，未知值降级为 MORNING。 */
public final class SchedulePeriodConverter implements SafeEnumConverter<SchedulePeriod> {

    @Override
    public Class<SchedulePeriod> enumType() {
        return SchedulePeriod.class;
    }

    @Override
    public SchedulePeriod defaultValue() {
        return SchedulePeriod.MORNING;
    }
}
