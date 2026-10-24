package com.oneshop.dto.response;

/**
 * Chain-wide numbers for the top of the Admin overview. Orders are counted once each (an Order supplied by several
 * Stores is still one Order); Fulfillments are the per-Store parts still open.
 */
public record AdminChainSummaryResponse(long totalOrders, long openFulfillments, long ordersNeedingAttention,
                                        long reconcilingOrders) {}
