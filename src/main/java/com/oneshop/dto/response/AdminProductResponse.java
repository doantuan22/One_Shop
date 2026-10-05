package com.oneshop.dto.response;

import com.oneshop.entity.ProductStatus;

/** Product as the Admin sees it: every status, with the ids needed to fill the edit form. */
public record AdminProductResponse(Long id, String sku, String name, String description, Long categoryId,
                                   String categoryName, Long brandId, String brandName, ProductStatus status) {
}
