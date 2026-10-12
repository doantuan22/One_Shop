package com.oneshop.dto.response;

import com.oneshop.entity.ProductStatus;

import java.math.BigDecimal;

/** Product as the Admin sees it: every status, with the ids needed to fill the edit form. */
public record AdminProductResponse(Long id, String sku, String name, String description, Long categoryId,
                                   String categoryName, Long brandId, String brandName, ProductStatus status,
                                   BigDecimal price, BigDecimal originalPrice) {

    public AdminProductResponse(Long id, String sku, String name, String description, Long categoryId,
                               String categoryName, Long brandId, String brandName, ProductStatus status) {
        this(id, sku, name, description, categoryId, categoryName, brandId, brandName, status, null, null);
    }
}
