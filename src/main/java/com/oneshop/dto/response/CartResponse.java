package com.oneshop.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * A cart as the customer sees it: lines grouped by Store.
 *
 * @param itemCount number of lines (not units)
 * @param total     sum of the lines that can be bought right now, over all Stores
 */
public record CartResponse(List<CartStoreGroupResponse> groups, int itemCount, BigDecimal total) {

    public static CartResponse empty() {
        return new CartResponse(List.of(), 0, BigDecimal.ZERO);
    }

    public boolean isEmpty() {
        return itemCount == 0;
    }

    public boolean hasSelectableItems() {
        return groups.stream().anyMatch(CartStoreGroupResponse::hasSelectableItems);
    }

    /** Ids of every line, in display order. */
    public List<Long> cartItemIds() {
        return groups.stream().flatMap(group -> group.items().stream()).map(CartItemResponse::cartItemId).toList();
    }
}
