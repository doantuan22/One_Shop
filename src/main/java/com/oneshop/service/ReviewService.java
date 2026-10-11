package com.oneshop.service;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.dto.response.ProductReviewsResponse;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.dto.response.ReviewableItemResponse;
import com.oneshop.dto.response.StaffReviewResponse;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Product reviews. Only a Customer with a COMPLETED Order of their own that contains the Product may review it,
 * once per Product (Roadmap V2 section 5, actor Customer). A review is visible immediately; Admin can hide it.
 * The review remembers the Order it was written for, so its Store is known; Staff of that Store (and Admin, for
 * every review) can answer it once, editing the answer in place.
 */
public interface ReviewService {

    /**
     * @param email   the authenticated Customer; never taken from the request body
     * @param orderId the COMPLETED Order the form was opened from, or {@code null} to use the most recent COMPLETED
     *                Order of the Customer that contains the Product
     * @throws com.oneshop.exception.BadRequestException not purchased/not completed, already reviewed, bad rating
     */
    ReviewResponse submit(String email, ReviewRequest request, Long orderId);

    /** Public: ACTIVE reviews only. */
    ProductReviewsResponse getProductReviews(Long productId);

    /** True only for an active Customer who completed a purchase of the Product and has not reviewed it yet. */
    boolean canReview(String emailOrNull, Long productId);

    /** The Products of the Customer's own COMPLETED Order, each flagged as reviewed or not. */
    List<ReviewableItemResponse> getOrderReviewItems(String email, Long orderId);

    /**
     * Visible reviews of the Stores the authenticated Staff is assigned to (ACTIVE assignment, ACTIVE Store),
     * newest first.
     */
    Page<StaffReviewResponse> getStaffReviews(int page);

    /**
     * Creates or replaces the single reply of a review. Staff may only answer a visible review of an Order of one of
     * their assigned Stores (anything else is "not found"); Admin may answer any review. The text is trimmed and must
     * be 1 to 1000 characters. {@code replied_by}/{@code replied_at} become the latest person and moment.
     */
    void reply(Long reviewId, String text);
}
