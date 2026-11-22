package com.sdas.server.identity.service.impl;

import com.sdas.server.identity.service.MaskService;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/**
 * 脱敏展示实现，格式与契约示例逐字一致。
 */
@Service
public class MaskServiceImpl implements MaskService {

    private static final String MASK = "*";

    @Override
    public String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return "";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    @Override
    public String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return name.substring(0, 1) + MASK;
    }

    @Override
    public String maskIdCard(String idCardNumber) {
        if (idCardNumber == null || idCardNumber.length() != 18) {
            return "";
        }
        return idCardNumber.substring(0, 3) + "***********" + idCardNumber.substring(14);
    }

    @Override
    public byte[] utf8Bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
