package com.oneshop.dto.response;

import com.oneshop.entity.VisibilityStatus;

public record CategoryResponse(Long id, String name, String description, VisibilityStatus status) {
}
