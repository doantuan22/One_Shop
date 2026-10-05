package com.oneshop.dto.response;

public record ProductImageResponse(Long id, String imageUrl, boolean primary, int sortOrder) {
}
