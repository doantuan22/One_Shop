package com.oneshop.service;

import com.oneshop.dto.response.OrderPaymentResponse;
import com.oneshop.dto.response.PaymentResponse;

/**
 * Payment records per Order for ONLINE, COD and PAY_AT_STORE (BR-18).
 *
 * <p>Phase 9.1 implements the internal ONLINE demo only. Amount/method/owner/status never come from a request.
 */
public interface PaymentService {
    OrderPaymentResponse getOrderPayments(String customerEmail, Long orderId);

    /** Returns the existing PENDING attempt on repeated clicks. FAILED cancels the Order permanently. */
    PaymentResponse createOnlinePaymentAttempt(String customerEmail, Long orderId);

    PaymentResponse markOnlinePaymentSuccess(String customerEmail, Long orderId, Long paymentId);

    PaymentResponse markOnlinePaymentFailed(String customerEmail, Long orderId, Long paymentId);
}
