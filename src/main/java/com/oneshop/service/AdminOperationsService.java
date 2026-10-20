package com.oneshop.service;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Chain-wide reads only. No Staff assignment predicate or fulfillment mutation. */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminOperationsService {
    private final OrderRepository orders;
    private final StoreProductRepository stock;
    private final StaffStoreAssignmentRepository assignments;
    private final StoreService stores;
    private static final List<OrderFulfillmentStatus> OPEN_FULFILLMENTS = List.of(OrderFulfillmentStatus.CONFIRMED,
            OrderFulfillmentStatus.PREPARING, OrderFulfillmentStatus.PACKED, OrderFulfillmentStatus.SHIPPING,
            OrderFulfillmentStatus.READY_FOR_PICKUP);
    private final OrderViewService views;
    private final OrderFulfillmentRepository fulfillments;
    private final PaymentDeadlinePolicy deadlines;
    public AdminOperationsService(OrderRepository orders, StoreProductRepository stock,
            StaffStoreAssignmentRepository assignments, StoreService stores, OrderViewService views,
            OrderFulfillmentRepository fulfillments, PaymentDeadlinePolicy deadlines) {
        this.deadlines = deadlines;
        this.orders = orders; this.stock = stock; this.assignments = assignments; this.stores = stores; this.views = views;
        this.fulfillments = fulfillments;
    }
    public AdminChainSummaryResponse getChainSummary() {
        var cutoff = deadlines.expiredBefore();
        return new AdminChainSummaryResponse(orders.count(), fulfillments.countByStatusIn(OPEN_FULFILLMENTS),
                orders.countAttention(cutoff), orders.countReconciling(cutoff));
    }
    public Page<AdminOrderSummaryResponse> getOrders(Long storeId, int page) {
        return orders.searchAdmin(storeId, PageRequest.of(Math.max(0, page), 20)).map(o ->
                new AdminOrderSummaryResponse(views.summary(o, false, null), o.getUser().getFullName(), o.getUser().getEmail()));
    }
    public Page<AdminOrderSummaryResponse> getOrdersNeedingAttention(Long storeId, int page) {
        return orders.searchAdminAttention(storeId, deadlines.expiredBefore(), PageRequest.of(Math.max(0, page), 20)).map(o ->
                new AdminOrderSummaryResponse(views.summary(o, false, null), o.getUser().getFullName(), o.getUser().getEmail()));
    }
    public AdminOrderDetailResponse getOrder(Long id) {
        var order = orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        return new AdminOrderDetailResponse(views.detail(order, false, null), order.getCheckoutSession().getId(),
                order.getUser().getId(), order.getUser().getFullName(), order.getUser().getEmail());
    }
    public List<AdminStoreOverviewResponse> getOverview(Long storeId) {
        var selected = storeId == null ? stores.getAllStores() : List.of(stores.getStore(storeId));
        var cutoff = deadlines.expiredBefore();
        return selected.stream().map(s -> new AdminStoreOverviewResponse(s,
                orders.countByStoreId(s.id()), orders.countAttentionByStoreId(s.id(), cutoff),
                orders.countByStoreIdAndOrderStatus(s.id(), OrderStatus.COMPLETED),
                orders.countByStoreIdAndOrderStatus(s.id(), OrderStatus.CANCELLED), stock.countByStoreId(s.id()),
                stock.countByStoreIdAndStatusAndQuantityBetween(s.id(), ActiveStatus.ACTIVE, 1, 5),
                stock.countByStoreIdAndStatusAndQuantity(s.id(), ActiveStatus.ACTIVE, 0),
                assignments.countEffectiveStaff(s.id()))).toList();
    }
}
