package com.oneshop.dto.response;

import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentRefundStatus;
import com.oneshop.service.payment.GatewayVerification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A row of the Admin payment console: an ONLINE Order whose payment needs a person or is being reconciled. */
public record AdminPaymentCaseResponse(Long orderId, String customerName, String customerEmail,
                                       OrderStatus orderStatus, OrderPaymentStatus paymentStatus,
                                       BigDecimal totalAmount, LocalDateTime createdAt,
                                       LocalDateTime paymentDeadline, LocalDateTime reconciliationDeadline,
                                       GatewayVerification.Result verification, PaymentRefundStatus refundStatus,
                                       com.oneshop.service.payment.PaymentCaseCategory category) {
}
