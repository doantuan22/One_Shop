package com.oneshop.service;

import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.impl.OrderServiceImpl;
import com.oneshop.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final PaymentService service = new PaymentServiceImpl(payments, orders, new OrderServiceImpl(history), inventory);
    private Order order;
    private Payment payment;

    @BeforeEach
    void setup() {
        User owner = new User();
        owner.setEmail("alice@test");
        order = new Order();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setUser(owner);
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("200000.00"));
        payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 20L);
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.ONLINE);
        payment.setAmount(order.getTotalAmount());
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(payments.findByIdAndOrderId(20L, 10L)).thenReturn(Optional.of(payment));
    }

    @Test
    void attemptUsesDatabaseOrderFieldsAndReusesPending() {
        when(payments.findFirstByOrderIdAndStatusOrderByIdAsc(10L, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var created = service.createOnlinePaymentAttempt("alice@test", 10L);
        assertThat(created.orderId()).isEqualTo(10L);
        assertThat(created.amount()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(created.method()).isEqualTo(PaymentMethod.ONLINE);
        assertThat(created.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(created.paidAt()).isNull();
        assertThat(created.transactionCode()).isNull();
        when(payments.findFirstByOrderIdAndStatusOrderByIdAsc(10L, PaymentStatus.PENDING)).thenReturn(Optional.of(payment));
        assertThat(service.createOnlinePaymentAttempt("alice@test", 10L).paymentId()).isEqualTo(20L);
        verify(payments, times(1)).saveAndFlush(any());
    }

    @Test
    void successIsIdempotentAndNeverRestoresOrDeductsStock() {
        var result = service.markOnlinePaymentSuccess("alice@test", 10L, 20L);
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.paidAt()).isNotNull();
        assertThat(result.transactionCode()).startsWith("PAY-");
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(service.markOnlinePaymentSuccess("alice@test", 10L, 20L)).isEqualTo(result);
        verify(history, times(1)).save(any());
        verifyNoInteractions(inventory);
    }

    @Test
    void failureIsIdempotentAndRestoresOnce() {
        var result = service.markOnlinePaymentFailed("alice@test", 10L, 20L);
        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.paidAt()).isNull();
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.FAILED);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        service.markOnlinePaymentFailed("alice@test", 10L, 20L);
        verify(inventory, times(1)).restoreForCancelledOrder(order);
        verify(history, times(1)).save(any());
        assertThatThrownBy(() -> service.createOnlinePaymentAttempt("alice@test", 10L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void conflictingResultsAreRefused() {
        service.markOnlinePaymentSuccess("alice@test", 10L, 20L);
        assertThatThrownBy(() -> service.markOnlinePaymentFailed("alice@test", 10L, 20L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(inventory);
    }

    @Test
    void failedCannotBecomeSuccessful() {
        service.markOnlinePaymentFailed("alice@test", 10L, 20L);
        assertThatThrownBy(() -> service.markOnlinePaymentSuccess("alice@test", 10L, 20L)).isInstanceOf(BadRequestException.class);
        verify(history, times(1)).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"COD", "PAY_AT_STORE"})
    void offlineMethodsCannotEnterOnlineWorkflow(PaymentMethod method) {
        order.setPaymentMethod(method);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> service.createOnlinePaymentAttempt("alice@test", 10L)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.markOnlinePaymentSuccess("alice@test", 10L, 20L)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.markOnlinePaymentFailed("alice@test", 10L, 20L)).isInstanceOf(BadRequestException.class);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(inventory, history);
    }

    @Test
    void ownershipIsCheckedBeforeAccessingPayments() {
        assertThatThrownBy(() -> service.createOnlinePaymentAttempt("bob@test", 10L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.markOnlinePaymentSuccess("bob@test", 10L, 20L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.markOnlinePaymentFailed("bob@test", 10L, 20L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(payments, inventory, history);
    }

    @Test
    void missingOrderOrAttemptIsNotFound() {
        assertThatThrownBy(() -> service.createOnlinePaymentAttempt("alice@test", 99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.markOnlinePaymentSuccess("alice@test", 10L, 99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inconsistentAmountOrMethodDoesNotChangeState() {
        payment.setAmount(BigDecimal.ONE);
        assertThatThrownBy(() -> service.markOnlinePaymentFailed("alice@test", 10L, 20L)).isInstanceOf(BadRequestException.class);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(PaymentMethod.COD);
        assertThatThrownBy(() -> service.markOnlinePaymentSuccess("alice@test", 10L, 20L)).isInstanceOf(BadRequestException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(inventory, history);
    }

    @Test
    void nonPayableOrderCannotCompleteAPendingAttempt() {
        order.setOrderStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.markOnlinePaymentSuccess("alice@test", 10L, 20L)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.markOnlinePaymentFailed("alice@test", 10L, 20L)).isInstanceOf(BadRequestException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
