package com.sdas.server.appointment.service.impl;

import com.sdas.server.appointment.support.StoredImage;
import com.sdas.server.appointment.service.ImageStorageService;
import com.sdas.server.common.BusinessException;
import com.sdas.server.common.ErrorCode;
import com.sdas.server.config.ImageStorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地文件系统图片存储实现。
 *
 * <p>二次校验：内容类型白名单 → {@code ImageIO.read} 内容嗅探（防伪装）→ 大小上限；
 * 通过后经 ResourceHandler 以 servePath 只读暴露。</p>
 */
@Service
public class ImageStorageServiceImpl implements ImageStorageService {

    private static final List<String> EXTENSIONS = List.of("jpg", "png", "webp");

    private final ImageStorageProperties properties;

    /**
     * 构造图片存储服务。
     *
     * @param properties 存储配置
     */
    public ImageStorageServiceImpl(ImageStorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void ensureDirectory() {
        try {
            Files.createDirectories(Path.of(properties.location()));
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建图片存储目录: " + properties.location(), exception);
        }
    }

    @Override
    public StoredImage store(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !properties.allowedContentTypes().contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
        }
        if (file.getSize() > properties.maxSizeBytes()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_SIZE);
        }
        if (ImageIO.read(file.getInputStream()) == null) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
        }
        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = Path.of(properties.location(), filename);
        file.transferTo(target.toFile());
        String url = properties.servePath() + "/" + filename;
        return new StoredImage(url, contentType, file.getSize());
    }
}
