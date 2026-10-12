package com.oneshop.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Central Cart as the customer sees it in V3.
 *
 * @param items     flat list of cart items (V3 primary)
 * @param itemCount number of lines (not units)
 * @param total     sum of the lines that can be bought right now
 * @param groups    legacy store grouping (deprecated in V3, kept for checkout compatibility)
 */
public record CartResponse(List<CartItemResponse> items, int itemCount, BigDecimal total, List<CartStoreGroupResponse> groups) {

    /** Legacy constructor for V2 compatibility where groups was passed as the first parameter */
    public CartResponse(List<CartStoreGroupResponse> groups, int itemCount, BigDecimal total) {
        this(groups != null ? groups.stream().flatMap(g -> g.items().stream()).toList() : List.of(),
             itemCount,
             total,
             groups != null ? groups : List.of());
    }

    public static CartResponse of(List<CartItemResponse> items, int itemCount, BigDecimal total) {
        return new CartResponse(items != null ? items : List.of(), itemCount, total, List.of());
    }

    public static CartResponse empty() {
        return new CartResponse(List.of(), 0, BigDecimal.ZERO, List.of());
    }

    public boolean isEmpty() {
        return itemCount == 0;
    }

    public boolean hasSelectableItems() {
        return items != null && items.stream().anyMatch(CartItemResponse::selectable);
    }

    /** Ids of every line, in display order. */
    public List<Long> cartItemIds() {
        return items != null ? items.stream().map(CartItemResponse::cartItemId).toList() : List.of();
    }
}
