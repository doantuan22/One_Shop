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

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L;

    private static final byte[] PNG_SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Override
    public ImageUploadResult uploadImage(MultipartFile file, String subFolder) {
        requireConfigured();
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ảnh để tải lên");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Dung lượng ảnh tối đa 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new BadRequestException("Tệp tải lên phải là hình ảnh");
        }
        String normalizedType = contentType.toLowerCase(java.util.Locale.ROOT).trim();
        if (!normalizedType.equals("image/jpeg") && !normalizedType.equals("image/jpg")
                && !normalizedType.equals("image/png") && !normalizedType.equals("image/webp")) {
            throw new BadRequestException("Định dạng ảnh không được hỗ trợ. Chỉ chấp nhận JPEG, PNG, WebP");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Không thể đọc nội dung tệp");
        }

        validateFileSignature(bytes);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", folderFor(subFolder),
                    "resource_type", "image"));
            String url = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");
            log.info("Uploaded image to Cloudinary: {}", publicId);
            return new ImageUploadResult(url, publicId);
        } catch (IOException ex) {
            throw new ImageStorageException("Không thể tải ảnh lên Cloudinary", ex);
        } catch (RuntimeException ex) {
            // The SDK reports a refused upload (bad content, bad credentials, quota...) as a plain RuntimeException
            // carrying Cloudinary's message. Never let it escape as an HTTP 500.
            if (isInvalidImage(ex)) {
                log.warn("Cloudinary rejected the uploaded file as an invalid image: {}", ex.getMessage());
                throw new BadRequestException("Tệp tải lên không phải hình ảnh hợp lệ");
            }
            log.warn("Cloudinary upload failed: {}", ex.getMessage());
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
        } catch (RuntimeException ex) {
            // Provider-side API errors surface as RuntimeException; callers only handle ImageStorageException.
            log.warn("Cloudinary delete failed for {}: {}", publicId, ex.getMessage());
            throw new ImageStorageException("Không thể xóa ảnh trên Cloudinary", ex);
        }
    }

    /** Cloudinary answers with messages such as "Invalid image file" or "Unsupported ..." for unusable content. */
    private static boolean isInvalidImage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("invalid image file") || text.contains("unsupported") || text.contains("empty file")
                || text.contains("file size too large");
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

    /**
     * Validates that the file signature (magic bytes) matches JPEG, PNG, or WebP.
     * Rejects SVG, executable, text, or any unknown formats.
     */
    static void validateFileSignature(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new BadRequestException("Tệp tải lên không phải hình ảnh hợp lệ");
        }

        // JPEG starts with FF D8 FF
        boolean isJpeg = (bytes[0] == (byte) 0xFF) && (bytes[1] == (byte) 0xD8) && (bytes[2] == (byte) 0xFF);
        if (isJpeg) {
            return;
        }

        // PNG starts with 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= PNG_SIGNATURE.length) {
            boolean isPng = true;
            for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                if (bytes[i] != PNG_SIGNATURE[i]) {
                    isPng = false;
                    break;
                }
            }
            if (isPng) {
                return;
            }
        }

        // WebP: RIFF (bytes 0..3) .... WEBP (bytes 8..11)
        boolean isWebP = bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        if (isWebP) {
            return;
        }

        throw new BadRequestException("Tệp tải lên không phải hình ảnh hợp lệ");
    }
}
