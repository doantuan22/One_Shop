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
    public Page<AdminOrderSummaryResponse> getOrders(Long storeId, String q, OrderStatus orderStatus,
            OrderPaymentStatus paymentStatus, FulfillmentType fulfillmentType,
            java.time.LocalDate from, java.time.LocalDate to, int page) {
        var words = orderQuery(q).isEmpty() ? com.oneshop.service.search.TextSearch.words(q) : java.util.List.<String>of();
        if (!words.isEmpty()) {
            var matched = orders.searchAdmin(storeId, "", "", orderStatus, paymentStatus, fulfillmentType,
                            startOfDay(from), endExclusiveOf(to), org.springframework.data.domain.Pageable.unpaged())
                    .getContent().stream()
                    .filter(o -> com.oneshop.service.search.TextSearch.matchesAll(words, o.getUser().getFullName(), o.getUser().getEmail()))
                    .toList();
            return com.oneshop.service.search.TextSearch.slice(matched, PageRequest.of(Math.max(0, page), 20)).map(this::summary);
        }
        return orders.searchAdmin(storeId, orderQuery(q), likePattern(q), orderStatus, paymentStatus, fulfillmentType,
                        startOfDay(from), endExclusiveOf(to), PageRequest.of(Math.max(0, page), 20))
                .map(this::summary);
    }

    /** Legacy convenience overload used by Phase 11 regression tests: unfiltered chain order list. */
    public Page<AdminOrderSummaryResponse> getOrders(Long storeId, int page) {
        return getOrders(storeId, null, null, null, null, null, null, page);
    }

    public Page<AdminOrderSummaryResponse> getOrdersNeedingAttention(Long storeId, int page) {
        return orders.searchAdminAttention(storeId, deadlines.expiredBefore(), PageRequest.of(Math.max(0, page), 20))
                .map(this::summary);
    }

    /** One list row: detached view + how many fulfillment parts exist and which Stores hold them. */
    private AdminOrderSummaryResponse summary(Order o) {
        var parts = fulfillments.findByOrderIdInOrderByIdAsc(List.of(o.getId()));
        var storeNames = parts.stream().map(p -> p.getStore().getName()).distinct().toList();
        return new AdminOrderSummaryResponse(views.summary(o, false, null), o.getUser().getFullName(),
                o.getUser().getEmail(), parts.size(), storeNames);
    }

    /** q matches an exact numeric Order id only; anything else is a text search on customer name / e-mail. */
    private static String orderQuery(String q) {
        var text = q == null ? "" : q.trim();
        return text.matches("\\d+") ? text : "";
    }

    /** lower-case contains pattern with ! as the escape character of the like expression. */
    private static String likePattern(String q) {
        var text = q == null ? "" : q.trim().toLowerCase();
        if (text.isEmpty() || text.matches("\\d+")) {
            return "";
        }
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static java.time.LocalDateTime startOfDay(java.time.LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /** the upper bound excludes the first instant after the chosen end date. */
    private static java.time.LocalDateTime endExclusiveOf(java.time.LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
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
