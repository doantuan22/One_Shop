package com.oneshop.service;

import com.cloudinary.Cloudinary;
import com.oneshop.config.CloudinaryConfig;
import com.oneshop.config.CloudinaryProperties;
import com.oneshop.exception.ImageStorageException;
import com.oneshop.service.impl.CloudinaryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
