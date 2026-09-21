package com.oneshop.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.oneshop.config.CloudinaryProperties;
import com.oneshop.dto.response.ImageUploadResult;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ImageStorageException;
import com.oneshop.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryServiceImpl implements CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryServiceImpl.class);

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;

    public CloudinaryServiceImpl(Cloudinary cloudinary, CloudinaryProperties properties) {
        this.cloudinary = cloudinary;
        this.properties = properties;
    }

    @Override
    public ImageUploadResult uploadImage(MultipartFile file, String subFolder) {
        requireConfigured();
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ảnh để tải lên");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("Tệp tải lên phải là hình ảnh");
        }
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folderFor(subFolder),
                    "resource_type", "image"));
            String url = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");
            log.info("Uploaded image to Cloudinary: {}", publicId);
            return new ImageUploadResult(url, publicId);
        } catch (IOException ex) {
            throw new ImageStorageException("Không thể tải ảnh lên Cloudinary", ex);
        }
    }

    @Override
    public void deleteImage(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            return;
        }
        requireConfigured();
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
            log.info("Deleted image from Cloudinary: {}", publicId);
        } catch (IOException ex) {
            throw new ImageStorageException("Không thể xóa ảnh trên Cloudinary", ex);
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ImageStorageException("Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, "
                    + "CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET.");
        }
    }

    private String folderFor(String subFolder) {
        String root = StringUtils.hasText(properties.folder()) ? properties.folder() : "";
        String sub = StringUtils.hasText(subFolder) ? subFolder : "";
        if (root.isEmpty()) {
            return sub;
        }
        return sub.isEmpty() ? root : root + "/" + sub;
    }
}
