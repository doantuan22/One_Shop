package com.oneshop.service;

import com.oneshop.entity.*;
import com.oneshop.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderViewServiceTest {
    private final OrderViewService views=new OrderViewService(mock(OrderItemRepository.class),mock(OrderStatusHistoryRepository.class),mock(PaymentRepository.class));
    @ParameterizedTest @EnumSource(OrderStatus.class)
    void visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus status) {
        Order order=new Order();Store store=new Store();store.setName("Historical store");order.setStore(store);
        order.setFulfillmentType(FulfillmentType.STORE_PICKUP);order.setOrderStatus(status);order.setPickupCode("ABCD2345");
        boolean visible=status==OrderStatus.READY_FOR_PICKUP || status==OrderStatus.COMPLETED;
        assertThat(views.summary(order,true,null).pickupCode()).isEqualTo(visible?"ABCD2345":null);
        assertThat(views.summary(order,false,null).pickupCode()).isNull();
        order.setFulfillmentType(FulfillmentType.DELIVERY);assertThat(views.summary(order,true,null).pickupCode()).isNull();
    }
}
