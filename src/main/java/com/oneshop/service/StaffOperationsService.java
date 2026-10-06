package com.oneshop.service;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.StoreProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/** Read-only Staff operations. Every public read reuses Phase 10.1 scope; no request Store selector or mutation. */
@Service
@Transactional(readOnly = true)
public class StaffOperationsService {
    public static final int LOW_STOCK_THRESHOLD = 5;
    private static final List<OrderStatus> PROCESSING = List.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING,
            OrderStatus.PACKED, OrderStatus.SHIPPING);
    private final StaffStoreScopeService scope;
    private final OrderRepository orders;
    private final StoreProductRepository products;
    private final OrderViewService views;

    public StaffOperationsService(StaffStoreScopeService scope, OrderRepository orders,
                                  StoreProductRepository products, OrderViewService views) {
        this.scope = scope; this.orders = orders; this.products = products; this.views = views;
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
        return new StaffOrderDetailResponse(views.detail(order, false, null), order.getUser().getFullName());
    }

    private Page<StaffOrderSummaryResponse> readOrders(Collection<Long> assignedIds, int page, int size) {
        return orders.findByStoreIdInOrderByCreatedAtDescIdDesc(assignedIds, PageRequest.of(page, size))
                .map(o -> new StaffOrderSummaryResponse(views.summary(o, false, null), o.getUser().getFullName()));
    }
}
