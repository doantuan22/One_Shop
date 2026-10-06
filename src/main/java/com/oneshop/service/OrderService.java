package com.oneshop.service;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.User;

/** Internal state/history foundation. There are no HTTP actions backed by the generic primitive. */
public interface OrderService {
    /**
     * Locks the Order and checks the caller's expected state before applying a fulfillment graph edge.
     * The trusted orchestration layer must authorize the actor and provide required fulfillment side effects
     * in its surrounding transaction. READY_FOR_PICKUP and COMPLETED are rules, not production actions in 9.2.
     * A null actor means system; note must contain only safe audit text (max 500 UTF-16 units).
     */
    void transition(Long orderId, OrderStatus expectedStatus, OrderStatus targetStatus, User actor, String note);

    /** Payment-only primitives. PaymentService owns the Order write lock and surrounding transaction. */
    void confirmAfterOnlinePayment(Order lockedOrder);

    void cancelAfterOnlinePaymentFailure(Order lockedOrder);
}
