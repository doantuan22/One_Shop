package com.oneshop.dto.response;

/** What must be persisted in SQL Server for an uploaded image. */
public record ImageUploadResult(String url, String publicId) {
}
