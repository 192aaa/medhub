package com.sdas.server.appointment.support;

/**
 * 图片存储结果（内部传输对象）。
 *
 * @param url          只读访问 URL
 * @param contentType 校验后内容类型
 * @param sizeBytes   字节大小
 */
public record StoredImage(String url, String contentType, long sizeBytes) {
}
