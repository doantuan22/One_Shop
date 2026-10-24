package com.oneshop.service;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared snapshot/timeline reader for authorized customer and staff pickup services. No authorization or mutation. */
@Component
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class OrderViewService {
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository histories;
    private final PaymentRepository payments;
    public OrderViewService(OrderItemRepository items, OrderStatusHistoryRepository histories, PaymentRepository payments) {
        this.items = items; this.histories = histories; this.payments = payments;
    }
    private OrderFulfillmentRepository fulfillments;

    /** Setter injection keeps the 3-argument constructor usable by unit tests. */
    @Autowired
    void setFulfillments(OrderFulfillmentRepository fulfillments) {
        this.fulfillments = fulfillments;
    }

    /**
     * The parts of an Order per Store. The pickup code is returned only when {@code showCustomerCode} (the owner of
     * the Order) and only while the goods wait at the Store.
     */
    public List<FulfillmentSummaryResponse> fulfillmentParts(Order order, boolean showCustomerCode) {
        if (fulfillments == null || order.getId() == null) {
            return List.of();
        }
        List<OrderFulfillment> parts = fulfillments.findWithStoreByOrderId(order.getId());
        if (parts.isEmpty()) {
            return List.of();
        }
        Map<Long, List<OrderItemResponse>> byPart = items.findByFulfillmentIdInOrderByIdAsc(
                        parts.stream().map(OrderFulfillment::getId).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.getFulfillment().getId(), java.util.LinkedHashMap::new,
                        Collectors.mapping(i -> new OrderItemResponse(i.getProductName(), i.getUnitPrice(),
                                i.getQuantity(), i.getSubtotal()), Collectors.toList())));
        return parts.stream().map(f -> new FulfillmentSummaryResponse(f.getId(), f.getStore().getId(),
                f.getStore().getName(), f.getStore().getAddress(), f.getFulfillmentType(), f.getStatus(),
                byPart.getOrDefault(f.getId(), List.of()), f.getTrackingCode(),
                showCustomerCode && f.getStatus() == OrderFulfillmentStatus.READY_FOR_PICKUP ? f.getPickupCode() : null,
                f.getReadyAt(), f.getCompletedAt())).toList();
    }

    public OrderDetailResponse detail(Order order, boolean showCustomerCode, PickupAction action) {
        Long id = order.getId();
        return new OrderDetailResponse(summary(order, showCustomerCode, action), items.findByOrderId(id).stream()
                .map(i -> new OrderItemResponse(i.getProductName(), i.getUnitPrice(), i.getQuantity(), i.getSubtotal())).toList(),
                histories.findByOrderIdOrderByChangedAtAscIdAsc(id).stream().map(h -> new OrderHistoryResponse(
                        h.getOldStatus(), h.getNewStatus(), h.getChangedBy() == null ? "Hệ thống" : h.getChangedBy().getFullName(),
                        historyNote(order, h.getNote(), showCustomerCode), h.getChangedAt())).toList(),
                payments.findByOrderIdOrderByIdAsc(id).stream().map(p -> new PaymentResponse(p.getId(), id, p.getMethod(),
                        p.getAmount(), p.getStatus(), p.getTransactionCode(), p.getPaidAt(), p.getCreatedAt())).toList(),
                fulfillmentParts(order, showCustomerCode));
    }
    public OrderViewResponse summary(Order o, boolean showCustomerCode, PickupAction action) {
        boolean visible = showCustomerCode && o.getFulfillmentType() == FulfillmentType.STORE_PICKUP
                && (o.getOrderStatus() == OrderStatus.READY_FOR_PICKUP || o.getOrderStatus() == OrderStatus.COMPLETED);
        Long storeId = o.getStore() == null ? null : o.getStore().getId();
        String storeName = o.getStore() == null ? multiStoreLabel(o) : o.getStore().getName();
        String storeAddress = o.getStore() == null ? null : o.getStore().getAddress();
        return new OrderViewResponse(o.getId(), storeId, storeName, storeAddress,
                o.getCreatedAt(), o.getFulfillmentType(), o.getPaymentMethod(), o.getPaymentStatus(), o.getOrderStatus(),
                o.getReceiverName(), o.getReceiverPhone(), o.getShippingAddress(), o.getTotalAmount(), o.getReadyAt(),
                o.getPickedUpAt(), visible ? o.getPickupCode() : null, action);
    }

    /** An Order supplied by several Stores has no single Store: name the number of Fulfillment Stores. */
    private static String multiStoreLabel(Order o) {
        long stores = o.getFulfillments().stream().map(f -> f.getStore().getId()).distinct().count();
        return stores > 1 ? "Nhiều chi nhánh (" + stores + ")" : "OneShop";
    }

    /** Legacy history notes may contain a stored code; hiding the summary field alone would still expose it to Staff. */
    private String historyNote(Order order, String note, boolean showCustomerCode) {
        String code = order.getPickupCode();
        if (!showCustomerCode && order.getFulfillmentType() == FulfillmentType.STORE_PICKUP
                && note != null && code != null && !code.isEmpty()) {
            return note.replace(code, "[ẩn mã nhận hàng]");
        }
        return note;
    }
}
