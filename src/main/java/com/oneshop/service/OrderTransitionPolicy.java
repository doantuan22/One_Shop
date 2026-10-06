package com.oneshop.service;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.oneshop.entity.OrderStatus.*;

/** Pure graph: no persistence, authorization, payment or fulfillment side effects. */
@Component
public class OrderTransitionPolicy {
    public enum Cause { FULFILLMENT, PAYMENT_SUCCESS, PAYMENT_FAILURE }

    private static final Map<FulfillmentType, Map<OrderStatus, Set<OrderStatus>>> GRAPH = Map.of(
            FulfillmentType.DELIVERY, Map.of(
                    PENDING_PAYMENT, Set.of(CONFIRMED, CANCELLED),
                    CONFIRMED, Set.of(PREPARING), PREPARING, Set.of(PACKED),
                    PACKED, Set.of(SHIPPING), SHIPPING, Set.of(COMPLETED)),
            FulfillmentType.STORE_PICKUP, Map.of(
                    PENDING_PAYMENT, Set.of(CONFIRMED, CANCELLED),
                    CONFIRMED, Set.of(PREPARING), PREPARING, Set.of(READY_FOR_PICKUP),
                    READY_FOR_PICKUP, Set.of(COMPLETED)));

    /** Complete graph, including payment-only edges. Null is never a business state. */
    public boolean canTransition(OrderStatus current, OrderStatus target, FulfillmentType fulfillment) {
        return current != null && target != null && fulfillment != null
                && GRAPH.get(fulfillment).getOrDefault(current, Set.of()).contains(target);
    }

    /** Normal internal callers cannot use the two payment-only edges. */
    public boolean canTransition(OrderStatus current, OrderStatus target, FulfillmentType fulfillment, Cause cause) {
        if (!canTransition(current, target, fulfillment) || cause == null) return false;
        Cause required = current == PENDING_PAYMENT
                ? (target == CONFIRMED ? Cause.PAYMENT_SUCCESS : Cause.PAYMENT_FAILURE)
                : Cause.FULFILLMENT;
        return cause == required;
    }
}
