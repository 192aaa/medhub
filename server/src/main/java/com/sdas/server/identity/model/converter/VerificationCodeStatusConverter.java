package com.sdas.server.identity.model.converter;

import com.sdas.server.identity.model.VerificationCodeStatus;

/** VerificationCodeStatus 显式转换器，未知值降级为 REVOKED（最保守状态）。 */
public final class VerificationCodeStatusConverter implements SafeEnumConverter<VerificationCodeStatus> {

    @Override
    public Class<VerificationCodeStatus> enumType() {
        return VerificationCodeStatus.class;
    }

    @Override
    public VerificationCodeStatus defaultValue() {
        return VerificationCodeStatus.REVOKED;
    }
}
