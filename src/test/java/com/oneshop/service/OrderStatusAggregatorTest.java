package com.oneshop.service;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.service.fulfillment.OrderStatusAggregator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.oneshop.entity.OrderFulfillmentStatus.CANCELLED;
import static com.oneshop.entity.OrderFulfillmentStatus.COMPLETED;
import static com.oneshop.entity.OrderFulfillmentStatus.CONFIRMED;
import static com.oneshop.entity.OrderFulfillmentStatus.FAILED;
import static com.oneshop.entity.OrderFulfillmentStatus.PACKED;
import static com.oneshop.entity.OrderFulfillmentStatus.PREPARING;
import static com.oneshop.entity.OrderFulfillmentStatus.READY_FOR_PICKUP;
import static com.oneshop.entity.OrderFulfillmentStatus.SHIPPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The aggregation rule of spec section 9.2/9.3, without a database. */
class OrderStatusAggregatorTest {

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of(List.of(CONFIRMED, CONFIRMED), true, OrderStatus.CONFIRMED),
                Arguments.of(List.of(PREPARING, CONFIRMED), true, OrderStatus.PROCESSING),
                Arguments.of(List.of(PACKED, PREPARING), true, OrderStatus.PROCESSING),
                Arguments.of(List.of(SHIPPING, PACKED), true, OrderStatus.SHIPPING),
                Arguments.of(List.of(COMPLETED, SHIPPING), true, OrderStatus.PARTIALLY_COMPLETED),
                Arguments.of(List.of(COMPLETED, CONFIRMED), true, OrderStatus.PARTIALLY_COMPLETED),
                Arguments.of(List.of(COMPLETED, COMPLETED), true, OrderStatus.COMPLETED),
                Arguments.of(List.of(FAILED, COMPLETED), true, OrderStatus.NEEDS_ATTENTION),
                Arguments.of(List.of(FAILED, CONFIRMED), true, OrderStatus.NEEDS_ATTENTION),
                Arguments.of(List.of(CANCELLED, CANCELLED), true, OrderStatus.CANCELLED),
                Arguments.of(List.of(READY_FOR_PICKUP), true, OrderStatus.PROCESSING),
                Arguments.of(List.of(READY_FOR_PICKUP, COMPLETED), true, OrderStatus.PARTIALLY_COMPLETED),
                Arguments.of(List.of(COMPLETED), true, OrderStatus.COMPLETED),
                Arguments.of(List.of(COMPLETED), false, OrderStatus.NEEDS_ATTENTION),
                Arguments.of(List.of(CONFIRMED), false, OrderStatus.CONFIRMED),
                Arguments.of(List.of(CANCELLED, COMPLETED), true, OrderStatus.COMPLETED));
    }

    @ParameterizedTest(name = "{0}, paid={1} -> {2}")
    @MethodSource("table")
    @DisplayName("derive follows the spec table")
    void derive(List<OrderFulfillmentStatus> statuses, boolean paid, OrderStatus expected) {
        assertThat(OrderStatusAggregator.derive(statuses, paid)).isEqualTo(expected);
    }

    @Test
    @DisplayName("nothing to derive without Fulfillments")
    void emptyIsNull() {
        assertThat(OrderStatusAggregator.derive(List.of(), true)).isNull();
        assertThat(OrderStatusAggregator.derive(null, true)).isNull();
    }

    @Test
    @DisplayName("a pickup waiting at the Store never completes the Order")
    void readyIsNotCompleted() {
        assertThat(OrderStatusAggregator.derive(List.of(READY_FOR_PICKUP, READY_FOR_PICKUP), true))
                .isNotEqualTo(OrderStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- recompute

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderFulfillmentRepository fulfillments = mock(OrderFulfillmentRepository.class);
    private final OrderStatusHistoryRepository histories = mock(OrderStatusHistoryRepository.class);
    private final OrderStatusAggregator aggregator = new OrderStatusAggregator(orders, fulfillments, histories);

    private Order order(OrderStatus status, OrderPaymentStatus payment) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(7L);
        when(order.getOrderStatus()).thenReturn(status);
        when(order.getPaymentStatus()).thenReturn(payment);
        return order;
    }

    @Test
    @DisplayName("never leaves PENDING_PAYMENT, never clears NEEDS_ATTENTION, never touches CANCELLED / COMPLETED")
    void protectedStatesAreKept() {
        for (OrderStatus protectedStatus : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.NEEDS_ATTENTION,
                OrderStatus.CANCELLED, OrderStatus.COMPLETED)) {
            Order order = order(protectedStatus, OrderPaymentStatus.PAID);
            when(fulfillments.findStatusesByOrderId(7L)).thenReturn(List.of(PREPARING, CONFIRMED));

            assertThat(aggregator.recompute(order, null, "x")).isNull();

            verify(order, never()).setOrderStatus(any());
        }
        verify(histories, never()).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("writes the new status and one history row when the derived status changes")
    void writesOnChange() {
        Order order = order(OrderStatus.CONFIRMED, OrderPaymentStatus.UNPAID);
        when(fulfillments.findStatusesByOrderId(7L)).thenReturn(List.of(PREPARING));

        assertThat(aggregator.recompute(order, null, "Tổng hợp")).isEqualTo(OrderStatus.PROCESSING);

        verify(order).setOrderStatus(OrderStatus.PROCESSING);
        verify(histories).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("writes nothing when the derived status is the current one")
    void noWriteWithoutChange() {
        Order order = order(OrderStatus.PROCESSING, OrderPaymentStatus.UNPAID);
        when(fulfillments.findStatusesByOrderId(7L)).thenReturn(List.of(PACKED, CONFIRMED));

        assertThat(aggregator.recompute(order, null, "x")).isNull();

        verify(order, never()).setOrderStatus(any());
        verify(histories, never()).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("a FAILED part moves a running Order to NEEDS_ATTENTION")
    void failedMovesToNeedsAttention() {
        Order order = order(OrderStatus.SHIPPING, OrderPaymentStatus.UNPAID);
        when(fulfillments.findStatusesByOrderId(7L)).thenReturn(List.of(FAILED));

        assertThat(aggregator.recompute(order, null, "x")).isEqualTo(OrderStatus.NEEDS_ATTENTION);
    }

    @Test
    @DisplayName("the aggregator does not cancel an Order by itself")
    void neverCancels() {
        Order order = order(OrderStatus.CONFIRMED, OrderPaymentStatus.UNPAID);
        when(fulfillments.findStatusesByOrderId(7L)).thenReturn(List.of(CANCELLED, CANCELLED));

        assertThat(aggregator.recompute(order, null, "x")).isNull();

        verify(order, never()).setOrderStatus(any());
    }
}
