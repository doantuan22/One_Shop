package com.oneshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(
        String cloudName,
        String apiKey,
        String apiSecret,
        String folder,
        @org.springframework.boot.context.properties.bind.DefaultValue("5") Integer connectTimeoutSeconds,
        @org.springframework.boot.context.properties.bind.DefaultValue("15") Integer readTimeoutSeconds,
        @org.springframework.boot.context.properties.bind.DefaultValue("15") Integer timeoutSeconds) {

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public CloudinaryProperties(
            String cloudName,
            String apiKey,
            String apiSecret,
            String folder,
            @org.springframework.boot.context.properties.bind.DefaultValue("5") Integer connectTimeoutSeconds,
            @org.springframework.boot.context.properties.bind.DefaultValue("15") Integer readTimeoutSeconds,
            @org.springframework.boot.context.properties.bind.DefaultValue("15") Integer timeoutSeconds) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.folder = folder;
        this.connectTimeoutSeconds = connectTimeoutSeconds != null ? connectTimeoutSeconds : 5;
        this.readTimeoutSeconds = readTimeoutSeconds != null ? readTimeoutSeconds : 15;
        this.timeoutSeconds = timeoutSeconds != null ? timeoutSeconds : 15;
    }

    public CloudinaryProperties(String cloudName, String apiKey, String apiSecret, String folder) {
        this(cloudName, apiKey, apiSecret, folder, 5, 15, 15);
    }

    public int effectiveTimeoutSeconds() {
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return timeoutSeconds;
        }
        if (readTimeoutSeconds != null && readTimeoutSeconds > 0) {
            return readTimeoutSeconds;
        }
        return 15;
    }

    public int effectiveConnectTimeoutSeconds() {
        return connectTimeoutSeconds != null && connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 5;
    }

    public int effectiveReadTimeoutSeconds() {
        return readTimeoutSeconds != null && readTimeoutSeconds > 0 ? readTimeoutSeconds : 15;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret);
    }
}
