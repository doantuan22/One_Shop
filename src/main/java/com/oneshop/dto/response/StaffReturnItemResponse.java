package com.oneshop.dto.response;

import com.oneshop.entity.ReturnDisposition;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One returned line waiting for (or already handled by) the Staff of the Store that delivered it.
 *
 * @param currentStock the Store's current quantity of the product, so Staff see what restocking changes
 */
public record StaffReturnItemResponse(Long returnItemId, Long returnId, Long orderId, String storeName,
                                      String productName, String sku, int quantity, BigDecimal subtotal,
                                      int currentStock, String reason, List<String> imageUrls,
                                      LocalDateTime approvedAt, ReturnDisposition disposition, String handlerName,
                                      LocalDateTime handledAt, String handleNote) {

    public boolean pending() {
        return disposition == ReturnDisposition.PENDING;
    }
}
