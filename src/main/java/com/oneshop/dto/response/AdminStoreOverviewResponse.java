package com.oneshop.dto.response;

public record AdminStoreOverviewResponse(StoreResponse store, long totalOrders, long processingOrders,
        long completedOrders, long cancelledOrders, long stockRows, long lowStockRows, long outOfStockRows,
        long assignedStaff) {}
