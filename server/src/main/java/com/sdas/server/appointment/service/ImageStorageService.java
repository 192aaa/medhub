package com.sdas.server.appointment.service;

import com.sdas.server.appointment.support.StoredImage;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/** 问诊图片存储服务（本地文件系统 + 内容嗅探）。 */
public interface ImageStorageService {

    /**
     * 校验并存储单张图片。
     *
     * @param file 上传文件
     * @return 存储结果（URL/类型/大小）
     * @throws IOException 存储失败
     */
    StoredImage store(MultipartFile file) throws IOException;
}
