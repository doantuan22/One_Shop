package com.oneshop.service;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.StoreProductRepository;
import com.oneshop.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/** Staff operations reuse Phase 10.1 scope and Phase 9 fulfillment; no request Store selector or new state rules. */
@Service
@Transactional(readOnly = true)
public class StaffOperationsService {
    public static final int LOW_STOCK_THRESHOLD = 5;
    private static final List<OrderStatus> PROCESSING = List.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING,
            OrderStatus.PACKED, OrderStatus.SHIPPING);
    private static final List<OrderStatus> PICKUP_QUEUE = List.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP);
    private final StaffStoreScopeService scope;
    private final OrderRepository orders;
    private final StoreProductRepository products;
    private final OrderViewService views;
    private final DeliveryFulfillmentService delivery;
    private final PickupFulfillmentService pickup;

    public StaffOperationsService(StaffStoreScopeService scope, OrderRepository orders,
                                  StoreProductRepository products, OrderViewService views,
                                  DeliveryFulfillmentService delivery, PickupFulfillmentService pickup) {
        this.scope = scope; this.orders = orders; this.products = products; this.views = views;
        this.delivery = delivery; this.pickup = pickup;
    }

    public StaffDashboardResponse getDashboard() {
        var assigned = scope.getAssignedStores();
        var counts = assigned.stream().map(s -> new StaffStoreDashboardResponse(s,
                orders.countByStoreIdAndOrderStatusIn(s.id(), PROCESSING),
                orders.countByStoreIdAndFulfillmentTypeAndOrderStatus(s.id(), FulfillmentType.STORE_PICKUP,
                        OrderStatus.READY_FOR_PICKUP),
                products.countByStoreIdAndStatusAndQuantityBetween(s.id(), ActiveStatus.ACTIVE, 1, LOW_STOCK_THRESHOLD),
                products.countByStoreIdAndStatusAndQuantity(s.id(), ActiveStatus.ACTIVE, 0))).toList();
        var recent = readOrders(assigned.stream().map(StoreResponse::id).toList(), 0, 5).getContent();
        return new StaffDashboardResponse(counts, LOW_STOCK_THRESHOLD, recent);
    }

    public Page<StaffOrderSummaryResponse> getOrders(int page) {
        return readOrders(scope.getAssignedStoreIds(), Math.max(0, page), 20);
    }

    public StaffOrderDetailResponse getOrder(Long orderId) {
        // Phase 10.1 performs id + assigned Store ids in SQL. Child data is read only AFTER that lookup succeeds.
        var order = scope.requireOrder(orderId);
        return new StaffOrderDetailResponse(views.detail(order, false, null), order.getUser().getFullName(),
                order.getFulfillmentType() == FulfillmentType.DELIVERY ? delivery.getNextAction(order) : null,
                order.getFulfillmentType() == FulfillmentType.STORE_PICKUP ? pickup.getNextAction(order) : null);
    }

    public Page<StaffOrderSummaryResponse> getPickupQueue(int page) {
        return orders.findByStoreIdInAndFulfillmentTypeAndOrderStatusInOrderByCreatedAtDescIdDesc(
                scope.getAssignedStoreIds(), FulfillmentType.STORE_PICKUP, PICKUP_QUEUE,
                PageRequest.of(Math.max(0, page), 20))
                .map(o -> new StaffOrderSummaryResponse(views.summary(o, false, null), o.getUser().getFullName()));
    }

    @Transactional
    public void performDelivery(Long orderId, DeliveryAction action) {
        String email = lockedStaffEmail(orderId);
        switch (action) {
            case PREPARE -> delivery.startPreparingDelivery(email, orderId);
            case PACK -> delivery.markDeliveryPacked(email, orderId);
            case SHIP -> delivery.markDeliveryShipping(email, orderId);
            case COMPLETE -> delivery.completeDelivery(email, orderId);
        }
    }

    @Transactional
    public void performPickup(Long orderId, PickupAction action, String code) {
        String email = lockedStaffEmail(orderId);
        switch (action) {
            case PREPARE -> pickup.startPreparingPickup(email, orderId);
            case READY -> pickup.markReadyForPickup(email, orderId);
            case COMPLETE -> pickup.completePickup(email, orderId, code);
        }
    }

    private String lockedStaffEmail(Long id) {
        scope.getAssignedStoreIds(); // Fail closed before looking up a resource when identity/assignment is invalid.
        if (id == null) throw new ResourceNotFoundException("Không tìm thấy đơn hàng của chi nhánh.");
        var order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của chi nhánh."));
        scope.requireAssignedStore(order.getStore().getId()); // Recheck the locked resource's actual Store, before writes.
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Page<StaffOrderSummaryResponse> readOrders(Collection<Long> assignedIds, int page, int size) {
        return orders.findByStoreIdInOrderByCreatedAtDescIdDesc(assignedIds, PageRequest.of(page, size))
                .map(o -> new StaffOrderSummaryResponse(views.summary(o, false, null), o.getUser().getFullName()));
    }
}
