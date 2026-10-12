package com.oneshop.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.oneshop.config.CloudinaryConfig;
import com.oneshop.config.CloudinaryProperties;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ImageStorageException;
import com.oneshop.service.impl.CloudinaryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudinaryServiceTest {

    @Test
    void configurationIsBuiltFromProperties() {
        CloudinaryProperties properties = new CloudinaryProperties("demo-cloud", "key", "secret", "oneshop");

        Cloudinary cloudinary = new CloudinaryConfig().cloudinary(properties);

        assertThat(properties.isConfigured()).isTrue();
        assertThat(cloudinary.config.cloudName).isEqualTo("demo-cloud");
        assertThat(cloudinary.config.apiKey).isEqualTo("key");
        assertThat(cloudinary.config.secure).isTrue();
    }

    @Test
    void applicationCanBeBuiltWithoutCredentialsButUploadFailsClearly() {
        CloudinaryProperties properties = new CloudinaryProperties("", "", "", "oneshop");
        CloudinaryService service = new CloudinaryServiceImpl(new CloudinaryConfig().cloudinary(properties), properties);
        MockMultipartFile image = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});

        assertThat(properties.isConfigured()).isFalse();
        assertThatThrownBy(() -> service.uploadImage(image, "products"))
                .isInstanceOf(ImageStorageException.class)
                .hasMessageContaining("CLOUDINARY_CLOUD_NAME");
        assertThatCode(() -> service.deleteImage(null)).doesNotThrowAnyException();
    }

    // ---- the SDK reports refused uploads/deletes as plain RuntimeException: they must not become HTTP 500 ----

    private static final byte[] VALID_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    private static final byte[] VALID_JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, (byte) 0xFF, (byte) 0xD9
    };
    private static final byte[] VALID_WEBP = new byte[] {
            'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
    };

    private static MockMultipartFile validPng() {
        return new MockMultipartFile("file", "valid.png", "image/png", VALID_PNG);
    }

    private static MockMultipartFile validJpeg() {
        return new MockMultipartFile("file", "valid.jpg", "image/jpeg", VALID_JPEG);
    }

    private static MockMultipartFile validWebp() {
        return new MockMultipartFile("file", "valid.webp", "image/webp", VALID_WEBP);
    }

    private static final CloudinaryProperties CONFIGURED = new CloudinaryProperties("demo-cloud", "key", "secret", "oneshop");

    private static CloudinaryService serviceWith(Uploader uploader) {
        Cloudinary cloudinary = mock(Cloudinary.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        return new CloudinaryServiceImpl(cloudinary, CONFIGURED);
    }

    @Test
    void uploadEmptyFileFailsAsBadRequest() {
        Uploader uploader = mock(Uploader.class);
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(empty, "products"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chọn ảnh");
        org.mockito.Mockito.verifyNoInteractions(uploader);
    }

    @Test
    void uploadOversizedFileFailsAsBadRequest() {
        Uploader uploader = mock(Uploader.class);
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(VALID_PNG, 0, largeBytes, 0, VALID_PNG.length);
        MockMultipartFile oversized = new MockMultipartFile("file", "large.png", "image/png", largeBytes);

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(oversized, "products"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("5MB");
        org.mockito.Mockito.verifyNoInteractions(uploader);
    }

    @Test
    void uploadSvgFileIsRejectedLocally() {
        Uploader uploader = mock(Uploader.class);
        byte[] svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=\"10\"/></svg>".getBytes();
        MockMultipartFile svg = new MockMultipartFile("file", "icon.svg", "image/svg+xml", svgBytes);

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(svg, "products"))
                .isInstanceOf(BadRequestException.class);
        org.mockito.Mockito.verifyNoInteractions(uploader);
    }

    @Test
    void uploadNonImageDisguisedAsPngIsRejectedByFileSignature() {
        Uploader uploader = mock(Uploader.class);
        MockMultipartFile fakePng = new MockMultipartFile("file", "fake.png", "image/png", "not really an image".getBytes());

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(fakePng, "products"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hình ảnh hợp lệ");
        org.mockito.Mockito.verifyNoInteractions(uploader);
    }

    @Test
    void validJpegAndWebpPassValidation() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://cdn/x.jpg", "public_id", "oneshop/products/x"));

        var jpegResult = serviceWith(uploader).uploadImage(validJpeg(), "products");
        assertThat(jpegResult.url()).isEqualTo("https://cdn/x.jpg");

        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://cdn/x.webp", "public_id", "oneshop/products/w"));
        var webpResult = serviceWith(uploader).uploadImage(validWebp(), "products");
        assertThat(webpResult.url()).isEqualTo("https://cdn/x.webp");
    }

    @Test
    void contentCloudinaryRefusesAsNotAnImageBecomesABadRequest() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenThrow(new RuntimeException("Invalid image file"));

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(validPng(), "products"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hình ảnh");
    }

    @Test
    void otherProviderFailuresOnUploadBecomeImageStorageException() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenThrow(new RuntimeException("Invalid Signature secret-details"));

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(validPng(), "products"))
                .isInstanceOf(ImageStorageException.class)
                .hasMessageNotContaining("secret-details");
    }

    @Test
    void ioFailureOnUploadStaysAnImageStorageException() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(validPng(), "products"))
                .isInstanceOf(ImageStorageException.class);
    }

    @Test
    void successfulUploadReturnsUrlAndPublicId() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://cdn/x.png", "public_id", "oneshop/products/x"));

        var result = serviceWith(uploader).uploadImage(validPng(), "products");

        assertThat(result.url()).isEqualTo("https://cdn/x.png");
        assertThat(result.publicId()).isEqualTo("oneshop/products/x");
    }

    @Test
    void providerFailureOnDeleteBecomesImageStorageException() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.destroy(anyString(), anyMap())).thenThrow(new RuntimeException("Invalid Signature"));

        assertThatThrownBy(() -> serviceWith(uploader).deleteImage("oneshop/products/x"))
                .isInstanceOf(ImageStorageException.class)
                .hasMessageContaining("xóa ảnh");
    }

    @Test
    void timeoutConfiguredInCloudinary() {
        CloudinaryProperties properties = new CloudinaryProperties("demo-cloud", "key", "secret", "oneshop", 5, 15, 15);
        Cloudinary cloudinary = new CloudinaryConfig().cloudinary(properties);
        assertThat(cloudinary.config.timeout).isEqualTo(15);
    }

    @Test
    void timeoutOnUploadThrowsImageStorageException() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenThrow(new java.net.SocketTimeoutException("Read timed out"));

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(validPng(), "products"))
                .isInstanceOf(ImageStorageException.class)
                .hasMessageContaining("Không thể tải ảnh lên Cloudinary");
    }

    @Test
    void transformUrlInsertsTransformationOnlyForCloudinaryUploads() {
        String cloudinaryUrl = "https://res.cloudinary.com/cloud/image/upload/v1234/oneshop/categories/xyz.webp";
        String transformed = CloudinaryService.transformUrl(cloudinaryUrl, 800);
        assertThat(transformed).isEqualTo("https://res.cloudinary.com/cloud/image/upload/f_auto,q_auto,w_800/v1234/oneshop/categories/xyz.webp");

        // Static URL remains unchanged
        String staticUrl = "/images/site/categories/category-skincare.webp";
        assertThat(CloudinaryService.transformUrl(staticUrl, 800)).isEqualTo(staticUrl);

        // Non-Cloudinary external URL remains unchanged
        String externalUrl = "https://example.com/assets/banner.jpg";
        assertThat(CloudinaryService.transformUrl(externalUrl, 800)).isEqualTo(externalUrl);

        // Null remains null
        assertThat(CloudinaryService.transformUrl(null, 800)).isNull();

        // Empty string remains empty
        assertThat(CloudinaryService.transformUrl("", 800)).isEmpty();

        // Already transformed remains unchanged
        assertThat(CloudinaryService.transformUrl(transformed, 800)).isEqualTo(transformed);
    }
}
