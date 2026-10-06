package com.oneshop.dto.response;

/** Counts of one assigned Store, calculated in SQL rather than from client-filtered rows. */
public record StaffStoreDashboardResponse(StoreResponse store, long pendingOrderCount, long waitingPickupCount,
                                          long lowStockSkuCount, long outOfStockSkuCount) { }
