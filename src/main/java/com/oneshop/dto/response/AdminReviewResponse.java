package com.oneshop.dto.response;

import com.oneshop.entity.VisibilityStatus;
import java.time.LocalDateTime;

public record AdminReviewResponse(Long id, String customerName, String customerEmail, Long productId,
        String sku, String productName, int rating, String comment, VisibilityStatus status, LocalDateTime createdAt,
        Long orderId, String storeName, String reply, LocalDateTime repliedAt,
        Long orderItemId, String legacyReply, LocalDateTime legacyRepliedAt) {
    public AdminReviewResponse(Long id, String customerName, String customerEmail, Long productId,
            String sku, String productName, int rating, String comment, VisibilityStatus status, LocalDateTime createdAt,
            Long orderId, String storeName, String reply, LocalDateTime repliedAt) {
        this(id, customerName, customerEmail, productId, sku, productName, rating, comment, status, createdAt,
                orderId, storeName, reply, repliedAt, null, null, null);
    }
}
