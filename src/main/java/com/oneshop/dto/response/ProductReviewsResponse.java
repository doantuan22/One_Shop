package com.oneshop.dto.response;

import java.util.List;

/** Visible (ACTIVE) reviews of a Product: the total, the average and the newest ones (bounded). */
public record ProductReviewsResponse(long count, double averageRating, List<ReviewResponse> latest) {

    public static ProductReviewsResponse empty() {
        return new ProductReviewsResponse(0, 0, List.of());
    }
}
