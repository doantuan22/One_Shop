package com.oneshop.service.payment.payos;

import java.util.List;

/**
 * The slice of payOS the application uses, expressed in plain types so the rest of the code (and its tests) does not
 * depend on the SDK. The only production implementation is {@link SdkPayosApi}.
 */
public interface PayosApi {

    /** @throws PayosException.Rejected payOS refused the request; @throws PayosException.Unavailable outcome unknown */
    LinkCreated createLink(LinkRequest request);

    /** @throws PayosException.NotFound there is no such link */
    LinkInfo getLink(long orderCode);

    /** Best effort; the result says what payOS now reports. */
    LinkInfo cancelLink(long orderCode, String reason);

    /** Verifies the signature of a raw webhook body. @throws PayosException.InvalidSignature when it does not verify */
    WebhookEvent verifyWebhook(String rawBody);

    /** Registers the webhook URL (payOS then POSTs a sample to it). */
    void confirmWebhook(String webhookUrl);

    record LinkRequest(long orderCode, long amount, String description, String itemName, String returnUrl,
                       String cancelUrl, long expiredAtEpochSeconds) {
    }

    record LinkCreated(String paymentLinkId, String checkoutUrl, long orderCode, long amount, String status) {
    }

    record Transaction(String reference, long amount, String transactionDateTime) {
    }

    /** {@code status} is the payOS PaymentLinkStatus name (PENDING, PROCESSING, PAID, UNDERPAID, CANCELLED, EXPIRED, FAILED). */
    record LinkInfo(String id, long orderCode, long amount, long amountPaid, long amountRemaining, String status,
                    List<Transaction> transactions) {
    }

    /**
     * A signature-verified webhook. {@code success}/{@code code} are the envelope's; {@code dataCode} is the
     * transaction's own code ("00" = successful transfer).
     */
    record WebhookEvent(boolean success, String code, Long orderCode, Long amount, String currency,
                        String paymentLinkId, String reference, String transactionDateTime, String dataCode) {
    }
}
