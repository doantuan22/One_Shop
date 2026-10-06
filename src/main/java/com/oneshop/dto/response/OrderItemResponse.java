package com.oneshop.dto.response;

import java.math.BigDecimal;

/** One line of an Order, exactly as it was frozen at checkout (never the current StoreProduct price). */
public record OrderItemResponse(String productName, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
}
