package com.oneshop.service;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.OrderPaymentStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.oneshop.entity.OrderStatus.*;

/** Transition rules: pure checks without persistence, authorization or payment/fulfillment side effects. */
@Component
public class OrderTransitionPolicy {
    /** Customer-only cancellation: before preparation and before collection; no refund workflow exists. */
    public boolean canCustomerCancel(OrderStatus status, FulfillmentType type, PaymentMethod method,
                                     OrderPaymentStatus payment) {
        if (payment != OrderPaymentStatus.UNPAID || type == null || method == null) return false;
        boolean validMethod = type == FulfillmentType.DELIVERY
                ? method == PaymentMethod.COD || method == PaymentMethod.ONLINE
                : method == PaymentMethod.PAY_AT_STORE || method == PaymentMethod.ONLINE;
        return validMethod && (method == PaymentMethod.ONLINE
                ? status == OrderStatus.PENDING_PAYMENT : status == OrderStatus.CONFIRMED);
    }

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
