package com.oneshop.entity;

/** What a simulated refund gives back (values of {@code payment_refunds.component}). */
public enum PaymentRefundComponent {
    /** the value of the goods of one Fulfillment that could not be delivered */
    GOODS,
    /** the one shipping fee of the Order, refunded only when nothing at all was delivered */
    SHIPPING,
    /** the whole collected amount at once (customer cancel before preparation; legacy refunds) */
    WHOLE_ORDER,
    /** the goods value of an Order the customer returned and an Administrator approved (the shipping fee is kept) */
    RETURN
}
