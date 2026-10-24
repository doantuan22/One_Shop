package com.oneshop.dto.response;

import com.oneshop.entity.PaymentRefundStatus;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.service.payment.LedgerState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One ONLINE payment row as the Admin payment console shows it, with its gateway ledger state. */
public record AdminPaymentAttemptResponse(Long paymentId, PaymentStatus status, LedgerState ledgerState,
                                          String reference, BigDecimal amount, LocalDateTime paidAt,
                                          PaymentRefundStatus refundStatus, BigDecimal refundAmount,
                                          LocalDateTime refundedAt, String refundReason, LocalDateTime createdAt) {
}
