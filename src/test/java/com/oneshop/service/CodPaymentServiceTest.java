package com.oneshop.service;

import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodPaymentServiceTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderService stateMachine = mock(OrderService.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final PaymentService service = new PaymentServiceImpl(payments, orders, stateMachine, inventory);
    private Order order;

    @BeforeEach
    void setup() {
        order = new Order();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setOrderStatus(OrderStatus.SHIPPING);
        order.setTotalAmount(new BigDecimal("175000.00"));
        when(payments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void collectedPaymentUsesLockedOrderSnapshotAndDoesNotTransitionOrTouchInventory() {
        var response = service.recordCodCollected(order);
        var captured = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getOrder()).isSameAs(order);
        assertThat(response.orderId()).isEqualTo(10L);
        assertThat(response.method()).isEqualTo(PaymentMethod.COD);
        assertThat(response.amount()).isEqualByComparingTo("175000");
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.paidAt()).isNotNull();
        assertThat(response.paidAt().getNano()).isZero();
        assertThat(response.transactionCode()).matches("PAY-[0-9a-f-]{36}");
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SHIPPING);
        verifyNoInteractions(orders, stateMachine, inventory);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "SHIPPING")
    void cannotCollectEarlyOrAtTerminalState(OrderStatus status) {
        order.setOrderStatus(status);
        reject();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, mode = EnumSource.Mode.EXCLUDE, names = "COD")
    void cannotCollectOtherMethods(PaymentMethod method) {
        order.setPaymentMethod(method);
        reject();
    }

    @ParameterizedTest
    @EnumSource(value = OrderPaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "UNPAID")
    void cannotCollectPaidOrFailedOrder(OrderPaymentStatus status) {
        order.setPaymentStatus(status);
        assertThatThrownBy(() -> service.recordCodCollected(order)).isInstanceOf(BadRequestException.class);
        assertThat(order.getPaymentStatus()).isEqualTo(status);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void cannotCollectPickupOrMalformedOrder() {
        order.setFulfillmentType(FulfillmentType.STORE_PICKUP);
        reject();
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setTotalAmount(new BigDecimal("-1"));
        reject();
        order.setTotalAmount(null);
        reject();
        assertThatThrownBy(() -> service.recordCodCollected(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void successfulReceiptAlreadyPresentCannotBeCollectedAgainEvenIfOrderSaysUnpaid() {
        when(payments.findFirstByOrderIdAndStatusOrderByIdAsc(10L, PaymentStatus.SUCCESS)).thenReturn(Optional.of(new Payment()));
        reject();
    }

    private void reject() {
        assertThatThrownBy(() -> service.recordCodCollected(order)).isInstanceOf(BadRequestException.class);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);
        verify(payments, never()).saveAndFlush(any());
        verifyNoInteractions(stateMachine, inventory);
    }
}
