package com.sdas.server.doctor.model.converter;

import com.sdas.server.doctor.model.PrescriptionStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** PrescriptionStatus 显式转换器，未知值降级为 DRAFT。 */
public final class PrescriptionStatusConverter implements SafeEnumConverter<PrescriptionStatus> {

    @Override
    public Class<PrescriptionStatus> enumType() {
        return PrescriptionStatus.class;
    }

    @Override
    public PrescriptionStatus defaultValue() {
        return PrescriptionStatus.DRAFT;
    }
}
