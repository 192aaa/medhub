package com.sdas.server.doctor.model.converter;

import com.sdas.server.doctor.model.MessageType;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** MessageType 显式转换器，未知值降级为 SYSTEM。 */
public final class MessageTypeConverter implements SafeEnumConverter<MessageType> {

    @Override
    public Class<MessageType> enumType() {
        return MessageType.class;
    }

    @Override
    public MessageType defaultValue() {
        return MessageType.SYSTEM;
    }
}
