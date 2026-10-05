package com.oneshop.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart lines of one Store (BR-06). At checkout each group becomes one Order.
 *
 * @param subtotal sum of the lines that can be bought right now
 */
public record CartStoreGroupResponse(Long storeId, String storeName, String storeAddress, boolean storeActive,
                                     List<CartItemResponse> items, BigDecimal subtotal) {

    public boolean hasSelectableItems() {
        return items.stream().anyMatch(CartItemResponse::selectable);
    }
}
