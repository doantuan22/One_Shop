package com.oneshop.dto.response;

import org.springframework.data.domain.Page;

public record StaffInventoryHistoryResponse(StoreProductStockResponse stock, Page<InventoryMovementResponse> movements) { }
