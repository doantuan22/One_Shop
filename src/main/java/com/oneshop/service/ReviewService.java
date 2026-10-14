package com.oneshop.service;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.dto.response.ProductReviewsResponse;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.dto.response.ReviewableItemResponse;
import com.oneshop.dto.response.StaffReviewResponse;
import org.springframework.data.domain.Page;
import java.util.List;

/** V3-BE-06: one review per received OrderItem; Staff Reply authority follows its verified Fulfillment Store. */
public interface ReviewService {
    /** Customer identity comes from authentication. Optional orderId/productId are checked context, never authority. */
    ReviewResponse submit(String email, ReviewRequest request, Long orderId);
    ProductReviewsResponse getProductReviews(Long productId);
    boolean canReview(String emailOrNull, Long productId);
    List<ReviewableItemResponse> getProductReviewItems(String emailOrNull, Long productId);
    List<ReviewableItemResponse> getOrderReviewItems(String email, Long orderId);
    Page<StaffReviewResponse> getStaffReviews(int page);
    StaffReviewResponse getStaffReview(Long id);
    /** Staff with a current ACTIVE assignment only. Admin moderates but cannot reply (OPEN-06 decided). */
    void reply(Long reviewId, String text);
}
