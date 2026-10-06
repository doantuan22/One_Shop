package com.oneshop.service;

import com.oneshop.dto.response.DeliveryAction;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.*;
import com.oneshop.service.impl.DeliveryFulfillmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryFulfillmentServiceTest {
    private static final String STAFF = "staff@test";
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderStatusHistoryRepository histories = mock(OrderStatusHistoryRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StoreService stores = mock(StoreService.class);
    private final OrderService stateMachine = mock(OrderService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final DeliveryFulfillmentService service = new DeliveryFulfillmentServiceImpl(orders, items, histories,
            paymentRepository, users, stores, stateMachine, payments);
    private Order order;
    private User actor;

    @BeforeEach
    void setup() {
        Store store = new Store();
        ReflectionTestUtils.setField(store, "id", 1L);
        store.setName("Store assigned");
        actor = new User();
        actor.setEmail(STAFF);
        actor.setRole(new Role(RoleName.STAFF));
        order = new Order();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setStore(store);
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(users.findByEmail(STAFF)).thenReturn(Optional.of(actor));
    }

    private void invoke(DeliveryAction action) {
        switch (action) {
            case PREPARE -> service.startPreparingDelivery(STAFF, 10L);
            case PACK -> service.markDeliveryPacked(STAFF, 10L);
            case SHIP -> service.markDeliveryShipping(STAFF, 10L);
            case COMPLETE -> service.completeDelivery(STAFF, 10L);
        }
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void fixedActionsLockScopeAndUseStateMachineWithTrustedActor(DeliveryAction action) {
        order.setOrderStatus(action.getExpectedStatus());
        invoke(action);
        var sequence = inOrder(orders, stores, payments, stateMachine);
        sequence.verify(orders).findByIdForUpdate(10L);
        sequence.verify(stores).requireAssignedStore(STAFF, 1L);
        if (action == DeliveryAction.COMPLETE) sequence.verify(payments).recordCodCollected(order);
        else verifyNoInteractions(payments);
        sequence.verify(stateMachine).transition(10L, action.getExpectedStatus(), action.getTargetStatus(), actor, action.getLabel());
        verifyNoInteractions(histories, items, paymentRepository);
    }

    static Stream<Arguments> states() {
        return Stream.of(DeliveryAction.values()).flatMap(action -> Stream.of(OrderStatus.values())
                .filter(state -> state != action.getExpectedStatus()).map(state -> Arguments.of(action, state)));
    }

    @ParameterizedTest
    @MethodSource("states")
    void wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction action, OrderStatus status) {
        order.setOrderStatus(status);
        reject(action);
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void pickupFlowIsRejectedForEveryDeliveryAction(DeliveryAction action) {
        order.setOrderStatus(action.getExpectedStatus());
        order.setFulfillmentType(FulfillmentType.STORE_PICKUP);
        reject(action);
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void malformedDeliveryPayAtStoreIsRejectedWithoutChangingMethod(DeliveryAction action) {
        // SQL CHECK forbids this combination; in-memory fixture exercises the service's defensive guard.
        order.setOrderStatus(action.getExpectedStatus());
        order.setPaymentMethod(PaymentMethod.PAY_AT_STORE);
        reject(action);
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.PAY_AT_STORE);
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void unpaidOnlineIsRejectedAtEveryStage(DeliveryAction action) {
        order.setOrderStatus(action.getExpectedStatus());
        order.setPaymentMethod(PaymentMethod.ONLINE);
        reject(action);
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void paidOnlineNeverCollectsOrRewritesPayment(DeliveryAction action) {
        order.setOrderStatus(action.getExpectedStatus());
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        invoke(action);
        verify(stateMachine).transition(10L, action.getExpectedStatus(), action.getTargetStatus(), actor, action.getLabel());
        verifyNoInteractions(payments, paymentRepository);
    }

    @ParameterizedTest
    @EnumSource(DeliveryAction.class)
    void scopeRejectionCannotBeBypassedByCallingServiceDirectly(DeliveryAction action) {
        doThrow(new AccessDeniedException("wrong store")).when(stores).requireAssignedStore(STAFF, 1L);
        assertThatThrownBy(() -> invoke(action)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(stateMachine, payments);
    }

    @Test
    void inactiveOrNonStaffActorIsRejectedEvenWithMockedScope() {
        actor.setStatus(ActiveStatus.INACTIVE);
        assertThatThrownBy(() -> invoke(DeliveryAction.PREPARE)).isInstanceOf(AccessDeniedException.class);
        actor.setStatus(ActiveStatus.ACTIVE);
        actor.setRole(new Role(RoleName.ADMIN));
        assertThatThrownBy(() -> invoke(DeliveryAction.PREPARE)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(stateMachine, payments);
    }

    @Test
    void unknownOrderIs404AndNullOrderIs400() {
        assertThatThrownBy(() -> service.completeDelivery(STAFF, 99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.completeDelivery(STAFF, null)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(stateMachine, payments);
    }

    @Test
    void listIsFilteredByTrustedAssignmentIdsAndDelivery() {
        var store = new StoreResponse(1L, "TD", "Assigned", "", "", "", null, null, true, true, ActiveStatus.ACTIVE);
        when(stores.getAssignedStores(STAFF)).thenReturn(List.of(store));
        when(orders.findByStoreIdInAndFulfillmentTypeOrderByCreatedAtDescIdDesc(List.of(1L), FulfillmentType.DELIVERY)).thenReturn(List.of(order));
        assertThat(service.getDeliveryOrders(STAFF)).singleElement().satisfies(row -> {
            assertThat(row.orderId()).isEqualTo(10L);
            assertThat(row.nextAction()).isEqualTo(DeliveryAction.PREPARE);
        });
        order.setPaymentMethod(PaymentMethod.ONLINE);
        assertThat(service.getDeliveryOrders(STAFF).get(0).nextAction()).isNull();
        when(stores.getAssignedStores(STAFF)).thenReturn(List.of());
        assertThatThrownBy(() -> service.getDeliveryOrders(STAFF)).isInstanceOf(AccessDeniedException.class);
    }

    private void reject(DeliveryAction action) {
        assertThatThrownBy(() -> invoke(action)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(stateMachine, payments);
    }
}
