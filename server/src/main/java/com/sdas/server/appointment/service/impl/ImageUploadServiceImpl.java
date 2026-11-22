package com.sdas.server.appointment.service.impl;

import com.sdas.server.appointment.dto.ImageUploadResponse;
import com.sdas.server.appointment.entity.ConsultationImage;
import com.sdas.server.appointment.repository.ConsultationImageRepository;
import com.sdas.server.appointment.service.ImageStorageService;
import com.sdas.server.appointment.service.ImageUploadService;
import com.sdas.server.appointment.support.StoredImage;
import com.sdas.server.identity.model.AuditEventType;
import com.sdas.server.identity.service.AuditEventService;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 问诊图片上传实现：先存储后落库（consultation_id 暂为空），返回 imageId。 */
@Service
public class ImageUploadServiceImpl implements ImageUploadService {

    private final ImageStorageService imageStorageService;

    private final ConsultationImageRepository imageRepository;

    private final AuditEventService auditEventService;

    /**
     * 构造图片上传服务。
     *
     * @param imageStorageService 存储服务
     * @param imageRepository     图片仓储
     * @param auditEventService   审计
     */
    public ImageUploadServiceImpl(ImageStorageService imageStorageService,
                                 ConsultationImageRepository imageRepository,
                                 AuditEventService auditEventService) {
        this.imageStorageService = imageStorageService;
        this.imageRepository = imageRepository;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional
    public ImageUploadResponse upload(MultipartFile file, long patientId) throws IOException {
        StoredImage stored = imageStorageService.store(file);
        long imageId = imageRepository.insert(new ConsultationImage(null, null, stored.url(),
                stored.contentType(), stored.sizeBytes(), null, null));
        auditEventService.record(AuditEventType.IMAGE_UPLOADED, "SUCCESS", patientId, null, null,
                null);
        return new ImageUploadResponse(imageId, stored.url(), stored.contentType(),
                stored.sizeBytes());
    }
}
