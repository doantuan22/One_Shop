package com.oneshop.dto.response;

import com.oneshop.entity.InventoryMovementType;
import java.time.LocalDateTime;

/** Authorized Staff history view; never exposes an entity or an unscoped StoreProduct. */
public record InventoryMovementResponse(Long id, InventoryMovementType type, int quantityBefore, int quantityAfter,
                                        int quantityChange, Long staffId, String staffName, String note,
                                        LocalDateTime createdAt) { }
