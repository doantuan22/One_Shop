package com.oneshop.service.review;

import com.oneshop.entity.OrderItem;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Review;

import java.util.Objects;

/** Provenance is proved by the purchased line; legacy orders.store_id never grants review access. */
public final class ReviewSource {
    private ReviewSource() { }

    public static boolean consistent(OrderItem item) {
        if (item == null || item.getOrder() == null || item.getFulfillment() == null
                || item.getStoreProduct() == null || item.getStoreProduct().getProduct() == null
                || item.getFulfillment().getStore() == null || item.getStoreProduct().getStore() == null
                || item.getFulfillment().getOrder() == null) {
            return false;
        }
        return Objects.equals(item.getOrder().getId(), item.getFulfillment().getOrder().getId())
                && Objects.equals(item.getStoreProduct().getStore().getId(), item.getFulfillment().getStore().getId());
    }

    public static boolean received(OrderItem item) {
        return consistent(item) && item.getFulfillment().getStatus() == OrderFulfillmentStatus.COMPLETED
                && item.getOrder().getOrderStatus() != OrderStatus.CANCELLED
                && item.getOrder().getPaymentStatus() == OrderPaymentStatus.PAID;
    }

    /** A null source remains historical data, never a guessed branch assignment. */
    public static OrderItem verified(Review review) {
        OrderItem item = review.getOrderItem();
        if (!consistent(item) || item.getOrder().getUser() == null || review.getUser() == null
                || review.getProduct() == null
                || !Objects.equals(review.getUser().getId(), item.getOrder().getUser().getId())
                || !Objects.equals(review.getProduct().getId(), item.getStoreProduct().getProduct().getId())
                || (review.getOrder() != null && !Objects.equals(review.getOrder().getId(), item.getOrder().getId()))) {
            return null;
        }
        return item;
    }
}
