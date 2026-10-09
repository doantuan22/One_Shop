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
    private static final List<OrderStatus> PROCESSING = List.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING,
            OrderStatus.PACKED, OrderStatus.SHIPPING, OrderStatus.READY_FOR_PICKUP);
    private final OrderRepository orders;
    private final StoreProductRepository stock;
    private final StaffStoreAssignmentRepository assignments;
    private final StoreService stores;
    private final OrderViewService views;
    public AdminOperationsService(OrderRepository orders, StoreProductRepository stock,
            StaffStoreAssignmentRepository assignments, StoreService stores, OrderViewService views) {
        this.orders = orders; this.stock = stock; this.assignments = assignments; this.stores = stores; this.views = views;
    }
    public Page<AdminOrderSummaryResponse> getOrders(Long storeId, int page) {
        return orders.searchAdmin(storeId, PageRequest.of(Math.max(0, page), 20)).map(o ->
                new AdminOrderSummaryResponse(views.summary(o, false, null), o.getUser().getFullName(), o.getUser().getEmail()));
    }
    public AdminOrderDetailResponse getOrder(Long id) {
        var order = orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        return new AdminOrderDetailResponse(views.detail(order, false, null), order.getCheckoutSession().getId(),
                order.getUser().getId(), order.getUser().getFullName(), order.getUser().getEmail());
    }
    public List<AdminStoreOverviewResponse> getOverview(Long storeId) {
        var selected = storeId == null ? stores.getAllStores() : List.of(stores.getStore(storeId));
        return selected.stream().map(s -> new AdminStoreOverviewResponse(s,
                orders.countByStoreId(s.id()), orders.countByStoreIdAndOrderStatusIn(s.id(), PROCESSING),
                orders.countByStoreIdAndOrderStatus(s.id(), OrderStatus.COMPLETED),
                orders.countByStoreIdAndOrderStatus(s.id(), OrderStatus.CANCELLED), stock.countByStoreId(s.id()),
                stock.countByStoreIdAndStatusAndQuantityBetween(s.id(), ActiveStatus.ACTIVE, 1, 5),
                stock.countByStoreIdAndStatusAndQuantity(s.id(), ActiveStatus.ACTIVE, 0),
                assignments.countEffectiveStaff(s.id()))).toList();
    }
}
