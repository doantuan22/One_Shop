package com.oneshop.dto.response;

import java.util.List;

public record StaffDashboardResponse(List<StaffStoreDashboardResponse> stores, int lowStockThreshold,
                                     List<StaffOrderSummaryResponse> recentOrders) { }
