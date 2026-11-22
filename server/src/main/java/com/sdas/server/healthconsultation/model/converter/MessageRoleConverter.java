package com.sdas.server.healthconsultation.model.converter;

import com.sdas.server.healthconsultation.model.MessageRole;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/**
 * {@link MessageRole} 的安全转换器。
 *
 * <p>未知值降级为 {@link MessageRole#PATIENT}，禁止使用 {@code @Enumerated}。</p>
 */
public final class MessageRoleConverter implements SafeEnumConverter<MessageRole> {

    @Override
    public Class<MessageRole> enumType() {
        return MessageRole.class;
    }

    @Override
    public MessageRole defaultValue() {
        return MessageRole.PATIENT;
    }
}
