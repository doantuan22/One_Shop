package com.oneshop.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** What the customer's order list shows besides the Order itself: its product lines and how many parts it ships in. */
public record OrderListInfo(List<Line> lines, int fulfillmentCount) {

    /** One product line: the name and price are the snapshot taken when the Order was placed. */
    public record Line(String productName, String imageUrl, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
