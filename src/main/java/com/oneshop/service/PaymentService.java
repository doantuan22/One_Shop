package com.oneshop.service;

import com.oneshop.dto.response.OrderPaymentResponse;
import com.oneshop.dto.response.PaymentResponse;
import com.oneshop.entity.Order;

/**
 * Payment records per Order for ONLINE, COD and PAY_AT_STORE (BR-18).
 *
 * <p>ONLINE demo and internal COD collection. Amount/method/owner/status never come from a request.
 */
public interface PaymentService {
    OrderPaymentResponse getOrderPayments(String customerEmail, Long orderId);

    /** Returns the existing PENDING attempt on repeated clicks. FAILED cancels the Order permanently. */
    PaymentResponse createOnlinePaymentAttempt(String customerEmail, Long orderId);

    PaymentResponse markOnlinePaymentSuccess(String customerEmail, Long orderId, Long paymentId);

    PaymentResponse markOnlinePaymentFailed(String customerEmail, Long orderId, Long paymentId);

    /** Internal primitive: caller holds the managed Order write lock and DELIVERY completion transaction. */
    PaymentResponse recordCodCollected(Order lockedOrder);

    /** Internal primitive: caller verified the pickup code and holds the Order lock/completion transaction. */
    PaymentResponse recordPayAtStoreCollected(Order lockedOrder);

    /** Caller owns the unpaid Order lock and cancellation transaction; closes existing pending attempts. */
    void closePendingForCustomerCancellation(Order lockedOrder);
}
