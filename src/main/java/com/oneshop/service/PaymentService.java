package com.oneshop.service;

import com.oneshop.dto.response.OrderPaymentResponse;
import com.oneshop.dto.response.PaymentResponse;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillment;

/**
 * Payment records per Order for ONLINE, COD and PAY_AT_STORE (BR-18).
 *
 * <p>ONLINE demo and internal COD collection. Amount/method/owner/status never come from a request.
 */
public interface PaymentService {
    OrderPaymentResponse getOrderPayments(String customerEmail, Long orderId);

    /** Returns the existing PENDING attempt on repeated clicks. FAILED cancels the Order permanently. */
    PaymentResponse createOnlinePaymentAttempt(String customerEmail, Long orderId);

    /**
     * Creates the single PENDING attempt of an ONLINE Order that checkout has just created, for the whole
     * {@code total_amount}, with a backend-made mock transaction reference. Caller owns the transaction; nothing is
     * collected and no result is simulated here.
     */
    PaymentResponse initializeOnlineAttempt(Order order);

    /** Internal primitive: caller holds the managed Order write lock and DELIVERY completion transaction. */
    PaymentResponse recordCodCollected(Order lockedOrder);

    /** Internal primitive: caller verified the pickup code and holds the Order lock/completion transaction. */
    PaymentResponse recordPayAtStoreCollected(Order lockedOrder);

    /**
     * V3 collection primitive (BE-04): records the single offline payment of an Order when its one Fulfillment is being
     * completed. COD needs a SHIPPING delivery Fulfillment, PAY_AT_STORE a READY_FOR_PICKUP pickup Fulfillment whose
     * code the caller has verified. The Order must be unpaid, workable (not PENDING_PAYMENT / NEEDS_ATTENTION /
     * CANCELLED) and have exactly one Fulfillment, because the amount is the whole Order and never a part of it.
     * The caller owns the Order lock and the transaction; nothing is ever marked PAID otherwise.
     */
    PaymentResponse recordOfflineCollected(Order lockedOrder, OrderFulfillment fulfillment);

    /** Caller owns the unpaid Order lock and cancellation transaction; closes existing pending attempts. */
    void closePendingForCustomerCancellation(Order lockedOrder);
}
