package com.sdas.server.appointment.model.converter;

import com.sdas.server.appointment.model.PaymentStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** PaymentStatus 显式转换器，未知值降级为 UNPAID。 */
public final class PaymentStatusConverter implements SafeEnumConverter<PaymentStatus> {

    @Override
    public Class<PaymentStatus> enumType() {
        return PaymentStatus.class;
    }

    @Override
    public PaymentStatus defaultValue() {
        return PaymentStatus.UNPAID;
    }
}
