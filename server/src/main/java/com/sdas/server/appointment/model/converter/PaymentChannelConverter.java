package com.sdas.server.appointment.model.converter;

import com.sdas.server.appointment.model.PaymentChannel;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/** PaymentChannel 显式转换器，未知值降级为 MOCK。 */
public final class PaymentChannelConverter implements SafeEnumConverter<PaymentChannel> {

    @Override
    public Class<PaymentChannel> enumType() {
        return PaymentChannel.class;
    }

    @Override
    public PaymentChannel defaultValue() {
        return PaymentChannel.MOCK;
    }
}
