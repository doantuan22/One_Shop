package com.oneshop.dto.response;

import com.oneshop.entity.InventoryMovementType;
import java.time.LocalDateTime;

/**
 * One row of the staff central movement log: what changed, where it happened, who recorded it and (for
 * ORDER / CANCEL_ORDER movements) which Order the stock change belongs to. Scope and fields stay consistent
 * with {@link InventoryMovementResponse}; the extra columns are the per-StoreProduct identity.
 */
public record InventoryMovementLogResponse(Long id, InventoryMovementType type, Long storeProductId, String sku,
                                           String productName, Long storeId, String storeName,
                                           int quantityBefore, int quantityAfter, int quantityChange,
                                           Long staffId, String staffName, String note, Long orderId,
                                           LocalDateTime createdAt) { }
