package com.oneshop.service.payment.payos;

import com.oneshop.entity.Payment;
import com.oneshop.service.payment.PaymentLinkCanceller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Closes a payOS payment link once its Order is cancelled, AFTER the cancelling transaction commits (no payOS call is
 * ever made while an Order lock is held). It depends only on the API client so that the cancellation service can use
 * it without a dependency cycle with the payment flow.
 *
 * <p>Closing a link is not a refund and says nothing about money: a link that was already paid cannot be cancelled,
 * and a transfer that still lands is found by reconciliation and refunded through the refund workflow.
 */
@Component
@ConditionalOnProperty(name = "oneshop.payment.provider", havingValue = "PAYOS")
public class PayosLinkCanceller implements PaymentLinkCanceller {

    private static final Logger log = LoggerFactory.getLogger(PayosLinkCanceller.class);

    private final PayosApi api;

    public PayosLinkCanceller(PayosApi api) {
        this.api = api;
    }

    @Override
    public void cancelAfterCommit(Payment attempt) {
        if (!attempt.isPayos() || attempt.getProviderOrderCode() == null) {
            return;
        }
        long orderCode = attempt.getProviderOrderCode();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cancelQuietly(orderCode);
                }
            });
        } else {
            cancelQuietly(orderCode);
        }
    }

    /** Never throws: a link that was paid, already closed or never created simply stays as it is. */
    public void cancelQuietly(long orderCode) {
        try {
            api.cancelLink(orderCode, "Don hang OneShop da dong");
        } catch (RuntimeException ex) {
            log.info("payOS link {} was not cancelled: {}", orderCode, ex.getMessage());
        }
    }
}
