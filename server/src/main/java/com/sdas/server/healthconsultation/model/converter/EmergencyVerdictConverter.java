package com.sdas.server.healthconsultation.model.converter;

import com.sdas.server.healthconsultation.model.EmergencyVerdict;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/**
 * {@link EmergencyVerdict} 的安全转换器。
 *
 * <p>未知值降级为 {@link EmergencyVerdict#REJECTED}（不预警），但 {@code warned} 列独立存储，
 * 因此降级不会改变历史预警事实。</p>
 */
public final class EmergencyVerdictConverter implements SafeEnumConverter<EmergencyVerdict> {

    @Override
    public Class<EmergencyVerdict> enumType() {
        return EmergencyVerdict.class;
    }

    @Override
    public EmergencyVerdict defaultValue() {
        return EmergencyVerdict.REJECTED;
    }
}
