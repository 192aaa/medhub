package com.sdas.server.identity.model.converter;

import com.sdas.server.identity.model.AuthStatus;

/** AuthStatus 显式转换器，未知值降级为 UNVERIFIED。 */
public final class AuthStatusConverter implements SafeEnumConverter<AuthStatus> {

    @Override
    public Class<AuthStatus> enumType() {
        return AuthStatus.class;
    }

    @Override
    public AuthStatus defaultValue() {
        return AuthStatus.UNVERIFIED;
    }
}
