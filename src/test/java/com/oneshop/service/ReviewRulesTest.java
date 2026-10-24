package com.oneshop.service;

import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.review.ReviewContent;
import com.oneshop.service.review.ReviewReadMapper;
import com.oneshop.service.review.ReviewSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class ReviewRulesTest {
    private Review source() {
        User user = new User(); ReflectionTestUtils.setField(user, "id", 7L);
        Product product = new Product(); ReflectionTestUtils.setField(product, "id", 3L);
        Store store = new Store(); ReflectionTestUtils.setField(store, "id", 1L);
        Order order = new Order(); ReflectionTestUtils.setField(order, "id", 9L);
        order.setUser(user); order.setOrderStatus(OrderStatus.PARTIALLY_COMPLETED); order.setPaymentStatus(OrderPaymentStatus.PAID);
        StoreProduct stock = new StoreProduct(); stock.setStore(store); stock.setProduct(product);
        OrderFulfillment part = new OrderFulfillment(); part.setOrder(order); part.setStore(store); part.setStatus(OrderFulfillmentStatus.COMPLETED);
        OrderItem item = new OrderItem(); item.setOrder(order); item.setFulfillment(part); item.setStoreProduct(stock);
        Review review = new Review(); review.setUser(user); review.setProduct(product); review.setOrder(order); review.setOrderItem(item);
        return review;
    }

    @Test void eligibilityFollowsCompletedPartOfPartiallyCompletedOrder() {
        Review review = source();
        assertThat(ReviewSource.received(review.getOrderItem())).isTrue();
        review.getOrderItem().getFulfillment().setStatus(OrderFulfillmentStatus.SHIPPING);
        assertThat(ReviewSource.received(review.getOrderItem())).isFalse();
    }

    @Test void refundedOrCancelledOrdersAreNeverReviewable() {
        Review review = source(); review.getOrder().setPaymentStatus(OrderPaymentStatus.REFUNDED);
        assertThat(ReviewSource.received(review.getOrderItem())).isFalse();
        review.getOrder().setPaymentStatus(OrderPaymentStatus.PAID); review.getOrder().setOrderStatus(OrderStatus.CANCELLED);
        assertThat(ReviewSource.received(review.getOrderItem())).isFalse();
    }

    @Test void mismatchedOwnerProductOrStoreCannotGrantStaffAuthority() {
        Review review = source();
        assertThat(ReviewSource.verified(review)).isNotNull();
        Product other = new Product(); ReflectionTestUtils.setField(other, "id", 99L); review.setProduct(other);
        assertThat(ReviewSource.verified(review)).isNull();
        review = source(); User otherUser = new User(); ReflectionTestUtils.setField(otherUser, "id", 99L); review.setUser(otherUser);
        assertThat(ReviewSource.verified(review)).isNull();
        review = source(); Store otherStore = new Store(); ReflectionTestUtils.setField(otherStore, "id", 99L);
        review.getOrderItem().getStoreProduct().setStore(otherStore);
        assertThat(ReviewSource.verified(review)).isNull();
    }

    @Test void legacyReviewWithoutAnItemHasNoInferredSource() {
        Review review = source(); review.setOrderItem(null);
        assertThat(ReviewSource.verified(review)).isNull();
        assertThat(ReviewReadMapper.storeName(review)).isNull();
    }

    @Test void oldReplyRemainsReadableAndDedicatedReplyTakesDisplayPriority() {
        Review review = source(); review.reply("Ancienne réponse", review.getUser(), java.time.LocalDateTime.now());
        assertThat(ReviewReadMapper.reply(review)).isEqualTo("Ancienne réponse");
        ReviewReply reply = new ReviewReply(); reply.setContent("Nội dung hiện tại"); review.setReviewReply(reply);
        assertThat(ReviewReadMapper.reply(review)).isEqualTo("Nội dung hiện tại");
        assertThat(review.getReply()).isEqualTo("Ancienne réponse");
    }

    @Test void contentAllowsVietnameseMarkupAndTheExactSqlLengthBoundary() {
        assertThat(ReviewContent.comment("  Tốt <b>đẹp</b>\n  ")).isEqualTo("Tốt <b>đẹp</b>");
        assertThat(ReviewContent.reply("x".repeat(1000))).hasSize(1000);
        assertThat(ReviewContent.comment("  ")).isNull();
        assertThat(ReviewContent.comment(null)).isNull();
        assertThatThrownBy(() -> ReviewContent.reply("x".repeat(1001))).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"", "   ", "\t\n", "bad\u0000text", "bad\u0007text"})
    void blankOrControlCharacterRepliesAreRefused(String text) {
        assertThatThrownBy(() -> ReviewContent.reply(text)).isInstanceOf(BadRequestException.class);
    }
}
