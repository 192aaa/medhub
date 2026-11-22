package com.sdas.server.identity.model.converter;

import com.sdas.server.identity.model.SessionStatus;

/** SessionStatus 显式转换器，未知值降级为 REVOKED。 */
public final class SessionStatusConverter implements SafeEnumConverter<SessionStatus> {

    @Override
    public Class<SessionStatus> enumType() {
        return SessionStatus.class;
    }

    @Override
    public SessionStatus defaultValue() {
        return SessionStatus.REVOKED;
    }
}
