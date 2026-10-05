package com.oneshop.dto.response;

import com.oneshop.entity.VisibilityStatus;

/** {@code logoUrl} is the Cloudinary delivery URL; the public id stays on the server. */
public record BrandResponse(Long id, String name, String description, String logoUrl, VisibilityStatus status) {
}
