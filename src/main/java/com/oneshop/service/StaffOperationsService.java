package com.oneshop.service;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.service.fulfillment.StaffFulfillmentService;
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
    private final OrderFulfillmentRepository fulfillments;
    private final StaffFulfillmentService fulfillmentService;
    public StaffOperationsService(StaffStoreScopeService scope, OrderRepository orders,
                                  StoreProductRepository products, OrderViewService views,
                                  DeliveryFulfillmentService delivery, PickupFulfillmentService pickup,
                                  OrderFulfillmentRepository fulfillments, StaffFulfillmentService fulfillmentService) {
        this.scope = scope; this.orders = orders; this.products = products; this.views = views;
        this.delivery = delivery; this.pickup = pickup;
        this.fulfillments = fulfillments; this.fulfillmentService = fulfillmentService;
    }

    /**
     * V3: the counters are Fulfillments of the assigned Stores ({@code order_fulfillments.store_id}); an Order that
     * several Stores supply is counted once in each Store for its own part, and Orders held by the payment gate are
     * shown separately instead of as work to do.
     */
    public StaffDashboardResponse getDashboard() {
        var assigned = scope.getAssignedStores();
        var ids = assigned.stream().map(StoreResponse::id).toList();
        var byStore = new java.util.HashMap<Long, java.util.List<OrderFulfillmentRepository.StatusCount>>();
        fulfillments.countByStoreAndStatus(ids).forEach(c -> byStore.computeIfAbsent(c.getStoreId(),
                k -> new java.util.ArrayList<>()).add(c));
        var counts = assigned.stream().map(s -> {
            var rows = byStore.getOrDefault(s.id(), java.util.List.of());
            long open = 0;
            long blocked = 0;
            long waiting = 0;
            long shipping = 0;
            long failed = 0;
            for (var row : rows) {
                if (row.getStatus() == OrderFulfillmentStatus.FAILED) {
                    failed += row.getTotal();
                } else if (StaffFulfillmentService.openStatuses().contains(row.getStatus())) {
                    open += row.getTotal() - row.getBlocked();
                    blocked += row.getBlocked();
                    if (row.getStatus() == OrderFulfillmentStatus.READY_FOR_PICKUP) {
                        waiting += row.getTotal() - row.getBlocked();
                    } else if (row.getStatus() == OrderFulfillmentStatus.SHIPPING) {
                        shipping += row.getTotal() - row.getBlocked();
                    }
                }
            }
            return new StaffStoreDashboardResponse(s, open, waiting,
                    products.countByStoreIdAndStatusAndQuantityBetween(s.id(), ActiveStatus.ACTIVE, 1, LOW_STOCK_THRESHOLD),
                    products.countByStoreIdAndStatusAndQuantity(s.id(), ActiveStatus.ACTIVE, 0), shipping, blocked, failed);
        }).toList();
        return new StaffDashboardResponse(counts, LOW_STOCK_THRESHOLD, fulfillmentService.getRecent(ids, 5));
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
        if (order.getStore() == null) {
            throw new com.oneshop.exception.BadRequestException("Đơn hàng được cấp từ nhiều chi nhánh và xử lý theo từng phần giao; chưa thể thao tác theo cả đơn.");
        }
        scope.requireAssignedStore(order.getStore().getId()); // Recheck the locked resource's actual Store, before writes.
        if (fulfillments.countByOrderId(order.getId()) > 0) {
            throw new com.oneshop.exception.BadRequestException("Đơn hàng được xử lý theo từng phần giao của chi nhánh; không thể thao tác theo cả đơn. Vui lòng dùng màn hình phần giao hàng.");
        }
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Page<StaffOrderSummaryResponse> readOrders(Collection<Long> assignedIds, int page, int size) {
        return orders.findByStoreIdInOrderByCreatedAtDescIdDesc(assignedIds, PageRequest.of(page, size))
                .map(o -> new StaffOrderSummaryResponse(views.summary(o, false, null), o.getUser().getFullName()));
    }
}
