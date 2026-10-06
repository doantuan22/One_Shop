package com.oneshop.service;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.repository.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    public OrderDetailResponse detail(Order order, boolean showCustomerCode, PickupAction action) {
        Long id = order.getId();
        return new OrderDetailResponse(summary(order, showCustomerCode, action), items.findByOrderId(id).stream()
                .map(i -> new OrderItemResponse(i.getProductName(), i.getUnitPrice(), i.getQuantity(), i.getSubtotal())).toList(),
                histories.findByOrderIdOrderByChangedAtAscIdAsc(id).stream().map(h -> new OrderHistoryResponse(
                        h.getOldStatus(), h.getNewStatus(), h.getChangedBy() == null ? "Hệ thống" : h.getChangedBy().getFullName(),
                        h.getNote(), h.getChangedAt())).toList(),
                payments.findByOrderIdOrderByIdAsc(id).stream().map(p -> new PaymentResponse(p.getId(), id, p.getMethod(),
                        p.getAmount(), p.getStatus(), p.getTransactionCode(), p.getPaidAt(), p.getCreatedAt())).toList());
    }
    public OrderViewResponse summary(Order o, boolean showCustomerCode, PickupAction action) {
        boolean visible = showCustomerCode && o.getFulfillmentType() == FulfillmentType.STORE_PICKUP
                && (o.getOrderStatus() == OrderStatus.READY_FOR_PICKUP || o.getOrderStatus() == OrderStatus.COMPLETED);
        return new OrderViewResponse(o.getId(), o.getStore().getId(), o.getStore().getName(), o.getStore().getAddress(),
                o.getCreatedAt(), o.getFulfillmentType(), o.getPaymentMethod(), o.getPaymentStatus(), o.getOrderStatus(),
                o.getReceiverName(), o.getReceiverPhone(), o.getShippingAddress(), o.getTotalAmount(), o.getReadyAt(),
                o.getPickedUpAt(), visible ? o.getPickupCode() : null, action);
    }
}
