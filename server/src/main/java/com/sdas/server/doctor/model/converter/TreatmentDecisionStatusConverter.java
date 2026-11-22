package com.sdas.server.doctor.model.converter;

import com.sdas.server.doctor.model.TreatmentDecisionStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** 状态显式转换器，未知值降级为 EMPTY_FALLBACK（安全优先）。 */
public final class TreatmentDecisionStatusConverter
        implements SafeEnumConverter<TreatmentDecisionStatus> {

    @Override
    public Class<TreatmentDecisionStatus> enumType() {
        return TreatmentDecisionStatus.class;
    }

    @Override
    public TreatmentDecisionStatus defaultValue() {
        return TreatmentDecisionStatus.EMPTY_FALLBACK;
    }
}
