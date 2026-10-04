package com.oneshop.dto.response;

import com.oneshop.entity.ActiveStatus;

import java.math.BigDecimal;

/** Staff/Admin view of a StoreProduct. Includes the exact quantity, so it must never reach a Client. */
public record StoreProductStockResponse(Long storeProductId, Long storeId, String storeName, Long productId,
                                        String sku, String productName, BigDecimal price, int quantity,
                                        ActiveStatus status) {
}
