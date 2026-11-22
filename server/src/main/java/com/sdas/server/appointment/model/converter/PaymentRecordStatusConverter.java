package com.sdas.server.appointment.model.converter;

import com.sdas.server.appointment.model.PaymentRecordStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** PaymentRecordStatus 显式转换器，未知值降级为 PENDING。 */
public final class PaymentRecordStatusConverter implements SafeEnumConverter<PaymentRecordStatus> {

    @Override
    public Class<PaymentRecordStatus> enumType() {
        return PaymentRecordStatus.class;
    }

    @Override
    public PaymentRecordStatus defaultValue() {
        return PaymentRecordStatus.PENDING;
    }
}
