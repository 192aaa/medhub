package com.sdas.server.identity.model.converter;

import com.sdas.server.identity.model.IdentityVerificationResult;

/** IdentityVerificationResult 显式转换器，未知值降级为 REJECTED。 */
public final class IdentityVerificationResultConverter
        implements SafeEnumConverter<IdentityVerificationResult> {

    @Override
    public Class<IdentityVerificationResult> enumType() {
        return IdentityVerificationResult.class;
    }

    @Override
    public IdentityVerificationResult defaultValue() {
        return IdentityVerificationResult.REJECTED;
    }
}
