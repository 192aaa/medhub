package com.sdas.server.identity.model.converter;

import com.sdas.server.identity.model.AccountStatus;

/** AccountStatus 显式转换器，未知值降级为 ACTIVE。 */
public final class AccountStatusConverter implements SafeEnumConverter<AccountStatus> {

    @Override
    public Class<AccountStatus> enumType() {
        return AccountStatus.class;
    }

    @Override
    public AccountStatus defaultValue() {
        return AccountStatus.ACTIVE;
    }
}
