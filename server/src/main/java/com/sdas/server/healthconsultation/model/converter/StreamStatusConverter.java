package com.sdas.server.healthconsultation.model.converter;

import com.sdas.server.healthconsultation.model.StreamStatus;
import com.sdas.server.identity.model.converter.SafeEnumConverter;

/**
 * {@link StreamStatus} 的安全转换器。
 *
 * <p>未知值降级为 {@link StreamStatus#FAILED}：宁可标注「未完成」也不冒充完整回答。</p>
 */
public final class StreamStatusConverter implements SafeEnumConverter<StreamStatus> {

    @Override
    public Class<StreamStatus> enumType() {
        return StreamStatus.class;
    }

    @Override
    public StreamStatus defaultValue() {
        return StreamStatus.FAILED;
    }
}
