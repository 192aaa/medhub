package com.sdas.server.appointment.controller;

import com.sdas.server.appointment.dto.ImageUploadResponse;
import com.sdas.server.appointment.service.ImageUploadService;
import com.sdas.server.common.PatientIdExtractor;
import com.sdas.server.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 端点 7：上传问诊图片（需认证，multipart）。 */
@RestController
@RequestMapping("/api/v1/consultations")
public class ImageController {

    private final ImageUploadService imageUploadService;

    private final PatientIdExtractor patientIdExtractor;

    /**
     * 构造问诊图片控制器。
     *
     * @param imageUploadService 图片上传服务
     * @param patientIdExtractor 患者身份解析器
     */
    public ImageController(ImageUploadService imageUploadService,
                          PatientIdExtractor patientIdExtractor) {
        this.imageUploadService = imageUploadService;
        this.patientIdExtractor = patientIdExtractor;
    }

    /**
     * 上传图片（字段名 file）。
     *
     * @param file       图片文件
     * @param httpRequest HTTP 请求
     * @return 上传响应
     * @throws IOException 存储失败
     */
    @PostMapping("/images")
    public Result<ImageUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                              HttpServletRequest httpRequest) throws IOException {
        long patientId = patientIdExtractor.extract(httpRequest).patientId();
        return Result.success(imageUploadService.upload(file, patientId));
    }
}
