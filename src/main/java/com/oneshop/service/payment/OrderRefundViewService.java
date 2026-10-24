package com.oneshop.service.payment;

import com.oneshop.dto.response.OrderRefundSummaryResponse;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentRefund;
import com.oneshop.entity.PaymentRefundComponent;
import com.oneshop.entity.PaymentRefundStatus;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRefundRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of the money side of an Order for the screens: what was collected, what was given back in SIMULATION
 * (the refund ledger), what the shop keeps, and which Fulfillments were confirmed undeliverable and why. The same data
 * serves the Customer (their own Order only, no staff names) and the Admin (with who decided). Never writes.
 */
@Service
public class OrderRefundViewService {

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentRefundRepository ledger;
    private final OrderFulfillmentRepository fulfillments;

    public OrderRefundViewService(OrderRepository orders, PaymentRepository payments, PaymentRefundRepository ledger,
                                  OrderFulfillmentRepository fulfillments) {
        this.orders = orders;
        this.payments = payments;
        this.ledger = ledger;
        this.fulfillments = fulfillments;
    }

    /** The Customer's own Order; another customer's Order is "not found". Actor names are not exposed. */
    @Transactional(readOnly = true)
    public OrderRefundSummaryResponse forCustomer(String customerEmail, Long orderId) {
        Order order = orders.findByIdAndUserEmail(orderId, customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        return summary(order, false);
    }

    /** Any Order, for an Administrator (the caller is authorized by the URL rule and the controller). */
    @Transactional(readOnly = true)
    public OrderRefundSummaryResponse forAdmin(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        return summary(order, true);
    }

    private OrderRefundSummaryResponse summary(Order order, boolean withActors) {
        List<OrderFulfillment> parts = fulfillments.findByOrderIdOrderByAssignedAtAscIdAsc(order.getId());
        Map<Long, String> undeliverable = new LinkedHashMap<>();
        for (OrderFulfillment part : parts) {
            String reason = part.getCancelReason();
            if (part.getStatus() == OrderFulfillmentStatus.CANCELLED && reason != null
                    && reason.startsWith(AdminFulfillmentExceptionService.UNDELIVERABLE_PREFIX)) {
                undeliverable.put(part.getId(), reason.substring(AdminFulfillmentExceptionService.UNDELIVERABLE_PREFIX.length()));
            }
        }
        List<PaymentRefund> history = ledger.findByOrderIdOrdered(order.getId());
        // COD / pay-at-store collect nothing online, but a returned Order was paid on delivery: then what was
        // collected is the SUCCESS payment the return was refunded from
        boolean returned = history.stream().anyMatch(r -> r.getComponent() == PaymentRefundComponent.RETURN);
        BigDecimal collected = BigDecimal.ZERO;
        if (order.getPaymentMethod() == PaymentMethod.ONLINE || returned) {
            collected = payments.findByOrderIdOrderByIdAsc(order.getId()).stream()
                    .filter(p -> (returned || p.getMethod() == PaymentMethod.ONLINE) && p.getStatus() == PaymentStatus.SUCCESS)
                    .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        List<OrderRefundSummaryResponse.Line> lines = history.stream()
                .map(r -> new OrderRefundSummaryResponse.Line(r.getId(), r.getRequestedAt(), label(r.getComponent()),
                        r.getFulfillment() == null ? null : r.getFulfillment().getId(), r.getAmount(), r.getStatus(),
                        r.getReason(), withActors ? actor(r) : null)).toList();
        BigDecimal refunded = lines.stream().filter(l -> l.status() == PaymentRefundStatus.SIMULATED_REFUNDED)
                .map(OrderRefundSummaryResponse.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean closedWithUndelivered = order.getOrderStatus() == OrderStatus.COMPLETED && !undeliverable.isEmpty();
        return new OrderRefundSummaryResponse(collected, refunded, collected.subtract(refunded), lines, undeliverable,
                closedWithUndelivered);
    }

    private static String actor(PaymentRefund refund) {
        return refund.getRequestedBy() == null ? "Hệ thống" : refund.getRequestedBy().getFullName();
    }

    private static String label(PaymentRefundComponent component) {
        return switch (component) {
            case GOODS -> "Hoàn tiền hàng không giao được";
            case SHIPPING -> "Hoàn phí giao hàng (không có hàng nào được giao)";
            case WHOLE_ORDER -> "Hoàn tiền toàn bộ đơn";
            case RETURN -> "Hoàn tiền hàng trả lại (giữ lại phí giao hàng)";
        };
    }
}
