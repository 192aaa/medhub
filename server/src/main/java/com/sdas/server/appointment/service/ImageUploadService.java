package com.sdas.server.appointment.service;

import com.sdas.server.appointment.dto.ImageUploadResponse;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/** 问诊图片上传服务：校验存储 + 落库返回 imageId（契约端点 7）。 */
public interface ImageUploadService {

    /**
     * 上传并持久化图片。
     *
     * @param file      上传文件
     * @param patientId 当前患者
     * @return 上传响应（含 imageId）
     * @throws IOException 存储失败
     */
    ImageUploadResponse upload(MultipartFile file, long patientId) throws IOException;
}
