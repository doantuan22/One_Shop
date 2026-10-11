package com.oneshop.service;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.dto.response.ProductReviewsResponse;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.dto.response.ReviewableItemResponse;

import java.util.List;

/**
 * Product reviews. Only a Customer with a COMPLETED Order of their own that contains the Product may review it,
 * once per Product (Roadmap V2 section 5, actor Customer). A review is visible immediately; Admin can hide it.
 */
public interface ReviewService {

    /**
     * @param email the authenticated Customer; never taken from the request body
     * @throws com.oneshop.exception.BadRequestException not purchased/not completed, already reviewed, bad rating
     */
    ReviewResponse submit(String email, ReviewRequest request);

    /** Public: ACTIVE reviews only. */
    ProductReviewsResponse getProductReviews(Long productId);

    /** True only for an active Customer who completed a purchase of the Product and has not reviewed it yet. */
    boolean canReview(String emailOrNull, Long productId);

    /** The Products of the Customer's own COMPLETED Order, each flagged as reviewed or not. */
    List<ReviewableItemResponse> getOrderReviewItems(String email, Long orderId);
}
