package com.sdas.server.pharmacy.model.converter;

import com.sdas.server.identity.model.converter.SafeEnumConverter;
import com.sdas.server.pharmacy.model.PharmacyAccountStatus;

/**
 * PharmacyAccountStatus 显式转换器，null / 未知值降级为 {@code DISABLED}。
 *
 * <p>降级取 {@code DISABLED} 而非 {@code ACTIVE}：脏数据或未知状态一律按「不可用」处理，
 * 避免异常账号获得登录能力。</p>
 */
public final class PharmacyAccountStatusConverter
        implements SafeEnumConverter<PharmacyAccountStatus> {

    @Override
    public Class<PharmacyAccountStatus> enumType() {
        return PharmacyAccountStatus.class;
    }

    @Override
    public PharmacyAccountStatus defaultValue() {
        return PharmacyAccountStatus.DISABLED;
    }
}
