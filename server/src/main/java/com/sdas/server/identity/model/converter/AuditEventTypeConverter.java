package com.sdas.server.identity.model.converter;

import com.sdas.server.identity.model.AuditEventType;

/** AuditEventType 显式转换器，未知值降级为 IDENTITY_REJECTED（最保守审计类型）。 */
public final class AuditEventTypeConverter implements SafeEnumConverter<AuditEventType> {

    @Override
    public Class<AuditEventType> enumType() {
        return AuditEventType.class;
    }

    @Override
    public AuditEventType defaultValue() {
        return AuditEventType.IDENTITY_REJECTED;
    }
}
