package com.oneshop.service;

import com.oneshop.entity.Order;

/**
 * Order state machine, cancellation, history, Store scope and pickup code (BR-11 to BR-14).
 *
 * <p>Phase 9.1 exposes only the two payment-driven primitives below. The general state machine belongs to Phase 9.2.
 * Every status change is validated and recorded in
 * OrderStatusHistory; cancel, stock restore and history happen in one transaction. Staff access is always scoped by the
 * Store of the Staff assignment, never by a request parameter.
 */
public interface OrderService {
    /** Payment-only primitives. Caller owns the Order write lock and the surrounding transaction. */
    void confirmAfterOnlinePayment(Order lockedOrder);

    void cancelAfterOnlinePaymentFailure(Order lockedOrder);
}
