package com.oneshop.service;

import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderStatusHistoryRepository histories = mock(OrderStatusHistoryRepository.class);
    private final OrderService service = new OrderServiceImpl(orders, histories, new OrderTransitionPolicy());
    private Order order;

    @BeforeEach
    void setup() {
        order = new Order();
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    }

    @Test
    void preservesActorNoteAndOnlyChangesStatusAfterLock() {
        User actor = new User();
        service.transition(10L, OrderStatus.CONFIRMED, OrderStatus.PREPARING, actor, "Đã nhận xử lý");
        var captured = ArgumentCaptor.forClass(OrderStatusHistory.class);
        var sequence = inOrder(orders, histories);
        sequence.verify(orders).findByIdForUpdate(10L);
        sequence.verify(orders).saveAndFlush(order);
        sequence.verify(histories).save(captured.capture());
        assertThat(captured.getValue().getOrder()).isSameAs(order);
        assertThat(captured.getValue().getOldStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(captured.getValue().getNewStatus()).isEqualTo(OrderStatus.PREPARING);
        assertThat(captured.getValue().getChangedBy()).isSameAs(actor);
        assertThat(captured.getValue().getNote()).isEqualTo("Đã nhận xử lý");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PREPARING);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(order.getPickupCode()).isNull();
        assertThat(order.getReadyAt()).isNull();
        assertThat(order.getPickedUpAt()).isNull();
    }

    @Test
    void invalidSameOrStaleRequestsDoNotWrite() {
        for (var target : new OrderStatus[]{OrderStatus.CONFIRMED, OrderStatus.PACKED, OrderStatus.CANCELLED, OrderStatus.READY_FOR_PICKUP}) {
            assertThatThrownBy(() -> service.transition(10L, OrderStatus.CONFIRMED, target, null, null))
                    .isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> service.transition(10L, OrderStatus.PREPARING, OrderStatus.PREPARING, null, null))
                .isInstanceOf(BadRequestException.class);
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(histories);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void rejectsLongNoteBeforeMutatingAndAcceptsBoundary() {
        assertThatThrownBy(() -> service.transition(10L, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, "x".repeat(501)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(histories);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        service.transition(10L, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, "x".repeat(500));
        var captured = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(histories).save(captured.capture());
        assertThat(captured.getValue().getNote()).hasSize(500);
        assertThat(captured.getValue().getChangedBy()).isNull();
    }

    @Test
    void genericPrimitiveCannotConfirmOrCancelPendingOnlineOrder() {
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        for (var target : new OrderStatus[]{OrderStatus.CONFIRMED, OrderStatus.CANCELLED}) {
            assertThatThrownBy(() -> service.transition(10L, OrderStatus.PENDING_PAYMENT, target, null, null))
                    .isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(histories);
    }

    @Test
    void missingOrderOrNullInputDoesNotWrite() {
        assertThatThrownBy(() -> service.transition(11L, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.transition(null, OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.transition(10L, null, OrderStatus.PREPARING, null, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(histories);
    }
}
