package com.oneshop.dto.response;

import java.time.LocalDateTime;

/** A review of a Store the Staff is assigned to, with the Order it came from and the current reply, if any. */
public record StaffReviewResponse(Long id, String customerName, Long productId, String sku, String productName,
                                  int rating, String comment, LocalDateTime createdAt, Long orderId, String storeName,
                                  String reply, LocalDateTime repliedAt) {
}
