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

    private static final CloudinaryProperties CONFIGURED = new CloudinaryProperties("demo-cloud", "key", "secret", "oneshop");

    private static MockMultipartFile fakePng() {
        return new MockMultipartFile("file", "fake.png", "image/png", "not really an image".getBytes());
    }

    private static CloudinaryService serviceWith(Uploader uploader) {
        Cloudinary cloudinary = mock(Cloudinary.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        return new CloudinaryServiceImpl(cloudinary, CONFIGURED);
    }

    @Test
    void contentCloudinaryRefusesAsNotAnImageBecomesABadRequest() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenThrow(new RuntimeException("Invalid image file"));

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(fakePng(), "products"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hình ảnh");
    }

    @Test
    void otherProviderFailuresOnUploadBecomeImageStorageException() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenThrow(new RuntimeException("Invalid Signature secret-details"));

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(fakePng(), "products"))
                .isInstanceOf(ImageStorageException.class)
                .hasMessageNotContaining("secret-details");
    }

    @Test
    void ioFailureOnUploadStaysAnImageStorageException() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> serviceWith(uploader).uploadImage(fakePng(), "products"))
                .isInstanceOf(ImageStorageException.class);
    }

    @Test
    void successfulUploadReturnsUrlAndPublicId() throws IOException {
        Uploader uploader = mock(Uploader.class);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://cdn/x.png", "public_id", "oneshop/products/x"));

        var result = serviceWith(uploader).uploadImage(fakePng(), "products");

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
}
