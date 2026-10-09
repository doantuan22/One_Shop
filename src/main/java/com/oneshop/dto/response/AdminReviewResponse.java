package com.oneshop.dto.response;

import com.oneshop.entity.VisibilityStatus;
import java.time.LocalDateTime;

public record AdminReviewResponse(Long id, String customerName, String customerEmail, Long productId,
        String sku, String productName, int rating, String comment, VisibilityStatus status, LocalDateTime createdAt) {}
