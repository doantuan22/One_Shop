package com.oneshop.dto.response;

import com.oneshop.entity.OrderReturnStatus;
import com.oneshop.entity.ReturnDisposition;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A return request as the screens show it. The Customer view carries no Administrator or Staff names
 * ({@code reviewerName}, {@code handlerName} are {@code null}); the Admin view carries both.
 *
 * @param refundAmount goods value that is (or will be) refunded in simulation; the shipping fee is kept
 */
public record OrderReturnResponse(Long returnId, Long orderId, String customerName, String customerEmail,
                                  OrderReturnStatus status, String reason, LocalDateTime receivedAt,
                                  LocalDateTime requestedAt, BigDecimal refundAmount, String reviewerName,
                                  LocalDateTime reviewedAt, String reviewNote, LocalDateTime completedAt,
                                  List<Item> items, List<String> imageUrls) {

    /** @param handlerName Staff who chose the disposition ({@code null} for the Customer and while PENDING) */
    public record Item(Long returnItemId, String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal,
                       String storeName, ReturnDisposition disposition, String handlerName, LocalDateTime handledAt,
                       String handleNote) {
    }

    public long pendingItems() {
        return items.stream().filter(i -> i.disposition() == ReturnDisposition.PENDING).count();
    }
}
