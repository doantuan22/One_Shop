package com.oneshop.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    /** Always created so the application starts without credentials; CloudinaryService checks before use. */
    @Bean
    public Cloudinary cloudinary(CloudinaryProperties properties) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", nullToEmpty(properties.cloudName()),
                "api_key", nullToEmpty(properties.apiKey()),
                "api_secret", nullToEmpty(properties.apiSecret()),
                "secure", true));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
