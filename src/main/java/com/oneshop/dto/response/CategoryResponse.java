package com.oneshop.dto.response;

import com.oneshop.entity.VisibilityStatus;

public record CategoryResponse(Long id, String name, String description, String imageUrl, VisibilityStatus status) {

    public CategoryResponse(Long id, String name, String description, VisibilityStatus status) {
        this(id, name, description, null, status);
    }
}
