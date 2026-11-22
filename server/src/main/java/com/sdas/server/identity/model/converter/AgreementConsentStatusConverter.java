package com.sdas.server.identity.model.converter;

import com.sdas.server.identity.model.AgreementConsentStatus;

/** AgreementConsentStatus 显式转换器，未知值降级为 AGREED。 */
public final class AgreementConsentStatusConverter implements SafeEnumConverter<AgreementConsentStatus> {

    @Override
    public Class<AgreementConsentStatus> enumType() {
        return AgreementConsentStatus.class;
    }

    @Override
    public AgreementConsentStatus defaultValue() {
        return AgreementConsentStatus.AGREED;
    }
}
