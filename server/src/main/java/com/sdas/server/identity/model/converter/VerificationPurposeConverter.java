package com.sdas.server.identity.model.converter;

import com.sdas.server.identity.model.VerificationPurpose;

/** VerificationPurpose 显式转换器，未知值降级为 REGISTER。 */
public final class VerificationPurposeConverter implements SafeEnumConverter<VerificationPurpose> {

    @Override
    public Class<VerificationPurpose> enumType() {
        return VerificationPurpose.class;
    }

    @Override
    public VerificationPurpose defaultValue() {
        return VerificationPurpose.REGISTER;
    }
}
