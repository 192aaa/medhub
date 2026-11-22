package com.sdas.server.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 问诊图片存储配置（prefix = sdas.storage.images）。
 *
 * <p>取值来源：specs/002-appointment-consultation/data-model.md 第 3 节。
 * location 为本地磁盘目录（MVP）；servePath 经 ResourceHandler 只读暴露。</p>
 *
 * @param location            本地存储目录
 * @param allowedContentTypes 内容类型白名单
 * @param maxSizeBytes       单张上限
 * @param maxCount           单问诊单上限
 * @param servePath          只读访问路径
 */
@ConfigurationProperties(prefix = "sdas.storage.images")
public record ImageStorageProperties(String location, List<String> allowedContentTypes,
                                    long maxSizeBytes, int maxCount, String servePath) {

    /** 防御性拷贝：白名单以不可变副本保存，避免调用方回写；缺省为空表（全部拒绝，安全兜底）。 */
    public ImageStorageProperties {
        allowedContentTypes = allowedContentTypes == null ? List.of() : List.copyOf(allowedContentTypes);
    }
}
