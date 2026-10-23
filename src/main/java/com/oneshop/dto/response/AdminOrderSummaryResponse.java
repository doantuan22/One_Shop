package com.oneshop.dto.response;

import java.util.List;

/**
 * One Admin order list row: the detached order view plus the V3 fulfillment split across Stores —
 * how many parts the chain created and which Stores hold them (labels only; nothing mutable here).
 */
public record AdminOrderSummaryResponse(OrderViewResponse order, String customerName, String customerEmail,
        int fulfillmentCount, List<String> storeNames) {}
