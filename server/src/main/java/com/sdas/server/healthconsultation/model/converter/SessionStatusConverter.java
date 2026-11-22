package com.sdas.server.healthconsultation.model.converter;

import com.sdas.server.healthconsultation.model.SessionStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/**
 * {@link SessionStatus} 的安全转换器。
 *
 * <p>未知值降级为 {@link SessionStatus#ACTIVE}。</p>
 */
public final class SessionStatusConverter implements SafeEnumConverter<SessionStatus> {

    @Override
    public Class<SessionStatus> enumType() {
        return SessionStatus.class;
    }

    @Override
    public SessionStatus defaultValue() {
        return SessionStatus.ACTIVE;
    }
}
