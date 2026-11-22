package com.sdas.server.appointment.dto;

/** 图片上传响应（契约端点 7）。 */
public record ImageUploadResponse(Long imageId, String url, String contentType, long sizeBytes) {
}
