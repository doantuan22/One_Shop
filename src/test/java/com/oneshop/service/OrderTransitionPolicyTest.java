package com.oneshop.service;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTransitionPolicyTest {
    private final OrderTransitionPolicy policy = new OrderTransitionPolicy();
    // Independent specification, including every forbidden pair through the exhaustive product below.
    private static final Set<String> DELIVERY = Set.of("PENDING_PAYMENT>CONFIRMED", "PENDING_PAYMENT>CANCELLED",
            "CONFIRMED>PREPARING", "PREPARING>PACKED", "PACKED>SHIPPING", "SHIPPING>COMPLETED");
    private static final Set<String> PICKUP = Set.of("PENDING_PAYMENT>CONFIRMED", "PENDING_PAYMENT>CANCELLED",
            "CONFIRMED>PREPARING", "PREPARING>READY_FOR_PICKUP", "READY_FOR_PICKUP>COMPLETED");

    static Stream<Arguments> pairs() {
        return Stream.of(FulfillmentType.values()).flatMap(type -> Stream.of(OrderStatus.values()).flatMap(from ->
                Stream.of(OrderStatus.values()).map(to -> Arguments.of(type, from, to))));
    }

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @MethodSource("pairs")
    void completeMatrixAndPaymentCauseIsolation(FulfillmentType type, OrderStatus from, OrderStatus to) {
        boolean allowed = (type == FulfillmentType.DELIVERY ? DELIVERY : PICKUP).contains(from + ">" + to);
        assertThat(policy.canTransition(from, to, type)).isEqualTo(allowed);
        for (var cause : OrderTransitionPolicy.Cause.values()) {
            boolean rightCause = from == OrderStatus.PENDING_PAYMENT
                    ? cause == (to == OrderStatus.CONFIRMED ? OrderTransitionPolicy.Cause.PAYMENT_SUCCESS
                    : OrderTransitionPolicy.Cause.PAYMENT_FAILURE)
                    : cause == OrderTransitionPolicy.Cause.FULFILLMENT;
            assertThat(policy.canTransition(from, to, type, cause)).isEqualTo(allowed && rightCause);
        }
    }

    @Test
    void nullIsNotACreationTransitionOrValidContext() {
        assertThat(policy.canTransition(null, OrderStatus.CONFIRMED, FulfillmentType.DELIVERY)).isFalse();
        assertThat(policy.canTransition(OrderStatus.CONFIRMED, null, FulfillmentType.DELIVERY)).isFalse();
        assertThat(policy.canTransition(OrderStatus.CONFIRMED, OrderStatus.PREPARING, null)).isFalse();
        assertThat(policy.canTransition(OrderStatus.CONFIRMED, OrderStatus.PREPARING, FulfillmentType.DELIVERY, null)).isFalse();
    }
}
