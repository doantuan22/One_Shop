package com.oneshop.service.review;

import com.oneshop.dto.response.AdminReviewResponse;
import com.oneshop.dto.response.ReviewResponse;
import com.oneshop.dto.response.StaffReviewResponse;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.Review;

import java.time.LocalDateTime;

/** Immutable view models, built within the reader transaction. Legacy reply columns are never overwritten. */
public final class ReviewReadMapper {
    private ReviewReadMapper() { }

    public static String reply(Review r) {
        return r.getReviewReply() == null ? r.getReply() : r.getReviewReply().getContent();
    }

    public static LocalDateTime repliedAt(Review r) {
        return r.getReviewReply() == null ? r.getRepliedAt() : r.getReviewReply().getUpdatedAt();
    }

    public static String storeName(Review r) {
        OrderItem source = ReviewSource.verified(r);
        return source == null ? null : source.getFulfillment().getStore().getName();
    }

    public static ReviewResponse publicReview(Review r) {
        return new ReviewResponse(r.getId(), r.getUser().getFullName(), r.getRating(), r.getComment(),
                r.getCreatedAt(), reply(r), repliedAt(r), storeName(r));
    }

    public static StaffReviewResponse staffReview(Review r) {
        OrderItem source = ReviewSource.verified(r);
        if (source == null) throw new IllegalStateException("Staff review query returned an unverified source");
        return new StaffReviewResponse(r.getId(), r.getUser().getFullName(), r.getProduct().getId(),
                r.getProduct().getSku(), source.getProductName(), r.getRating(), r.getComment(), r.getCreatedAt(),
                source.getOrder().getId(), storeName(r), reply(r), repliedAt(r), source.getId(),
                source.getFulfillment().getId());
    }

    public static AdminReviewResponse adminReview(Review r) {
        OrderItem source = ReviewSource.verified(r);
        Long orderId = source != null ? source.getOrder().getId() : r.getOrder() == null ? null : r.getOrder().getId();
        return new AdminReviewResponse(r.getId(), r.getUser().getFullName(), r.getUser().getEmail(), r.getProduct().getId(),
                r.getProduct().getSku(), r.getProduct().getName(), r.getRating(), r.getComment(), r.getStatus(),
                r.getCreatedAt(), orderId, storeName(r), reply(r), repliedAt(r), source == null ? null : source.getId(),
                r.getReply(), r.getRepliedAt());
    }
}
