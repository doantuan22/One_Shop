package com.oneshop.service;

import com.oneshop.dto.response.PickupAction;
import com.oneshop.entity.*;
import com.oneshop.exception.*;
import com.oneshop.repository.*;
import com.oneshop.service.impl.PickupFulfillmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PickupFulfillmentServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PaymentRepository receipts = mock(PaymentRepository.class);
    private final StoreService stores = mock(StoreService.class);
    private final OrderService states = mock(OrderService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final PickupCodeService codes = spy(new PickupCodeService());
    private final OrderViewService views = mock(OrderViewService.class);
    private final PickupFulfillmentService service = new PickupFulfillmentServiceImpl(orders, users, receipts, stores, states, payments, codes, views);
    private Order order;
    private User staff;
    @BeforeEach void setup() {
        order = new Order(); ReflectionTestUtils.setField(order, "id", 10L);
        Store store = new Store(); ReflectionTestUtils.setField(store, "id", 1L); order.setStore(store);
        order.setFulfillmentType(FulfillmentType.STORE_PICKUP); order.setPaymentMethod(PaymentMethod.PAY_AT_STORE);
        order.setOrderStatus(OrderStatus.CONFIRMED); order.setTotalAmount(new BigDecimal("175000"));
        staff = new User(); staff.setEmail("staff"); staff.setRole(new Role(RoleName.STAFF));
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order)); when(users.findByEmail("staff")).thenReturn(Optional.of(staff));
    }
    private void invoke(PickupAction action, String code) {
        switch(action) {
            case PREPARE -> service.startPreparingPickup("staff", 10L);
            case READY -> service.markReadyForPickup("staff", 10L);
            case COMPLETE -> service.completePickup("staff", 10L, code);
        }
    }
    private void ready() { order.setOrderStatus(OrderStatus.READY_FOR_PICKUP); order.setPickupCode("ABCD2345"); order.setReadyAt(LocalDateTime.now()); }
    @ParameterizedTest @EnumSource(PickupAction.class)
    void fixedEdgesAuthorizeActorAndDelegateStatus(PickupAction action) {
        order.setOrderStatus(action.getExpectedStatus()); if(action == PickupAction.COMPLETE) ready();
        invoke(action, "ABCD2345");
        verify(stores).requireAssignedStore("staff", 1L);
        verify(states).transition(10L, action.getExpectedStatus(), action.getTargetStatus(), staff, action.getLabel());
        assertThat(order.getOrderStatus()).isEqualTo(action.getExpectedStatus()); // No status setter in orchestration.
        if(action == PickupAction.READY) { assertThat(order.getPickupCode()).matches("[A-HJ-NP-Z2-9]{8}"); assertThat(order.getReadyAt()).isNotNull(); }
        if(action == PickupAction.COMPLETE) { verify(payments).recordPayAtStoreCollected(order); assertThat(order.getPickedUpAt()).isNotNull(); }
        else { verifyNoInteractions(payments); assertThat(order.getPickedUpAt()).isNull(); }
    }
    static Stream<Arguments> invalidStates() {
        return Stream.of(PickupAction.values()).flatMap(a -> Stream.of(OrderStatus.values())
                .filter(s -> s != a.getExpectedStatus()).map(s -> Arguments.of(a, s)));
    }
    @ParameterizedTest @MethodSource("invalidStates")
    void wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction action, OrderStatus state) {
        order.setOrderStatus(state); assertThatThrownBy(() -> invoke(action, "ABCD2345")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(states, payments, codes); assertThat(order.getPickupCode()).isNull();
    }
    @ParameterizedTest @EnumSource(PickupAction.class)
    void deliveryOrCodPickupAreRejectedEvenOnSharedPrepareEdge(PickupAction action) {
        order.setOrderStatus(action.getExpectedStatus()); order.setFulfillmentType(FulfillmentType.DELIVERY);
        assertThatThrownBy(() -> invoke(action,"ABCD2345")).isInstanceOf(BadRequestException.class);
        order.setFulfillmentType(FulfillmentType.STORE_PICKUP); order.setPaymentMethod(PaymentMethod.COD);
        assertThatThrownBy(() -> invoke(action,"ABCD2345")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(states,payments,codes);
    }
    @ParameterizedTest @EnumSource(PickupAction.class)
    void everyActionRequiresPaidOnlineOrUnpaidPayAtStore(PickupAction action) {
        order.setOrderStatus(action.getExpectedStatus());
        for(var method : new PaymentMethod[]{PaymentMethod.ONLINE,PaymentMethod.PAY_AT_STORE}) {
            order.setPaymentMethod(method);
            for(var status : OrderPaymentStatus.values()) {
                if(status == (method == PaymentMethod.ONLINE ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID)) continue;
                order.setPaymentStatus(status);
                assertThatThrownBy(() -> invoke(action,"ABCD2345")).isInstanceOf(BadRequestException.class);
            }
        }
        verifyNoInteractions(states,payments,codes);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","wrong","ABCD234","ABCD23456","ABCD234!","ABCD2340","ABCD2341","ABCD234I","ABCD234O","ZZZZ9999"})
    void invalidCodeNeverChangesPaymentTimeOrFields(String supplied) {
        ready(); var code=order.getPickupCode(); var time=order.getReadyAt();
        assertThatThrownBy(() -> invoke(PickupAction.COMPLETE,supplied)).isInstanceOf(BadRequestException.class).hasMessage("Mã nhận hàng không hợp lệ.");
        assertThat(order.getPickupCode()).isEqualTo(code); assertThat(order.getReadyAt()).isEqualTo(time);
        assertThat(order.getPickedUpAt()).isNull(); verifyNoInteractions(states,payments);
    }
    @Test void onlineCodeAcceptsOuterSpacesAndLowercaseAndPreservesReceipt() {
        ready(); order.setPaymentMethod(PaymentMethod.ONLINE); order.setPaymentStatus(OrderPaymentStatus.PAID);
        invoke(PickupAction.COMPLETE,"  abcd2345  "); verifyNoInteractions(payments); assertThat(order.getPickedUpAt()).isNotNull();
    }
    @Test void readyFieldsExistBeforeStateMachineFlush() {
        order.setOrderStatus(OrderStatus.PREPARING);
        doAnswer(call -> { assertThat(order.getPickupCode()).isNotNull(); assertThat(order.getReadyAt()).isNotNull(); return null; })
                .when(states).transition(eq(10L),eq(OrderStatus.PREPARING),eq(OrderStatus.READY_FOR_PICKUP),any(),anyString());
        invoke(PickupAction.READY,null);
    }
    @Test void existingReadyFieldsNeverGetOverwritten() {
        order.setOrderStatus(OrderStatus.PREPARING); order.setPickupCode("ABCD2345");
        assertThatThrownBy(() -> invoke(PickupAction.READY,null)).isInstanceOf(BadRequestException.class);
        assertThat(order.getPickupCode()).isEqualTo("ABCD2345"); verifyNoInteractions(codes,states,payments);
    }
    @ParameterizedTest @EnumSource(PickupAction.class)
    void receiptAlreadyExistsCannotBeCollectedOrRepaired(PickupAction action) {
        order.setOrderStatus(action.getExpectedStatus()); if(action==PickupAction.COMPLETE) ready();
        when(receipts.findFirstByOrderIdAndStatusOrderByIdAsc(10L,PaymentStatus.SUCCESS)).thenReturn(Optional.of(new Payment()));
        assertThatThrownBy(() -> invoke(action,"ABCD2345")).isInstanceOf(BadRequestException.class); verifyNoInteractions(states,payments,codes);
    }
    @Test void scopeAndActiveStaffAreCheckedBeforeCodeVerificationOrMutation() {
        ready(); doThrow(new AccessDeniedException("scope")).when(stores).requireAssignedStore("staff",1L);
        assertThatThrownBy(() -> invoke(PickupAction.COMPLETE,"ABCD2345")).isInstanceOf(AccessDeniedException.class);
        reset(stores); staff.setStatus(ActiveStatus.INACTIVE);
        assertThatThrownBy(() -> invoke(PickupAction.COMPLETE,"ABCD2345")).isInstanceOf(AccessDeniedException.class);
        staff.setStatus(ActiveStatus.ACTIVE); staff.setRole(new Role(RoleName.ADMIN));
        assertThatThrownBy(() -> invoke(PickupAction.COMPLETE,"ABCD2345")).isInstanceOf(AccessDeniedException.class); verifyNoInteractions(codes,payments,states);
    }
    @Test void missingOrderOrIdFailsSafely() {
        assertThatThrownBy(() -> service.startPreparingPickup("staff",null)).isInstanceOf(BadRequestException.class);
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> invoke(PickupAction.PREPARE,null)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void missingStoredCodeRejectsCompletion() {
        ready(); order.setPickupCode(null);
        assertThatThrownBy(() -> invoke(PickupAction.COMPLETE,"ABCD2345")).isInstanceOf(BadRequestException.class); verifyNoInteractions(states,payments);
    }
    @Test void generatedCodesAreShortRandomAndKeepStringRepresentation() {
        var generated=java.util.stream.IntStream.range(0,100).mapToObj(i -> codes.generate()).toList();
        assertThat(generated).doesNotHaveDuplicates().allSatisfy(c -> assertThat(c).matches("[A-HJ-NP-Z2-9]{8}").hasSizeLessThanOrEqualTo(20));
        codes.verify("  abc23456 ","ABC23456");
    }
    @Test void existingSixCharacterSeedCodeCanBeVerifiedOnlyOnItsOwnOrder() {
        ready(); order.setPickupCode("K7Q2M9"); invoke(PickupAction.COMPLETE," k7q2m9 ");
        verify(payments).recordPayAtStoreCollected(order);
        assertThatThrownBy(()->codes.verify("K7Q2M9","ABCD2345")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->codes.verify("K7Q2M8","K7Q2M9")).isInstanceOf(BadRequestException.class);
        codes.verify("012345","012345"); // String equality preserves leading zeros in existing codes.
    }
}
