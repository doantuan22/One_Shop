package com.oneshop.dto.response;

import java.math.BigDecimal;

/** Chain-wide view of a SKU. In V3, price is unified online selling price on Product (BR-01). */
public record ProductResponse(Long id, String sku, String name, String description, String categoryName,
                              String brandName, String imageUrl, BigDecimal price, BigDecimal originalPrice) {

    public ProductResponse(Long id, String sku, String name, String description, String categoryName,
                           String brandName, String imageUrl) {
        this(id, sku, name, description, categoryName, brandName, imageUrl, null, null);
    }
}
