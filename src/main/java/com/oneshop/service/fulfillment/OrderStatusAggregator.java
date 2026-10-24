package com.oneshop.service.fulfillment;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.entity.User;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Derives {@code orders.order_status} from the Fulfillments of the Order (V3-BE-04, spec section 9.2 and 9.3). Staff
 * never edit an Order status: they change one Fulfillment and this class recomputes the Order inside the same
 * transaction.
 *
 * <p>Priority: FAILED gives NEEDS_ATTENTION; every live Fulfillment COMPLETED gives COMPLETED (when the money is
 * settled); some COMPLETED gives PARTIALLY_COMPLETED; some SHIPPING gives SHIPPING; some PREPARING / PACKED /
 * READY_FOR_PICKUP gives PROCESSING; all CONFIRMED gives CONFIRMED.
 *
 * <p>It never leaves {@code PENDING_PAYMENT} (the payment gate does), never clears {@code NEEDS_ATTENTION} (Admin
 * resolution is BE-05) and never cancels or un-cancels an Order (cancellation also restores stock and refunds).
 */
@Component
public class OrderStatusAggregator {

    private final OrderRepository orders;
    private final OrderFulfillmentRepository fulfillments;
    private final OrderStatusHistoryRepository histories;

    public OrderStatusAggregator(OrderRepository orders, OrderFulfillmentRepository fulfillments,
                                 OrderStatusHistoryRepository histories) {
        this.orders = orders;
        this.fulfillments = fulfillments;
        this.histories = histories;
    }

    /**
     * Pure rule. Returns the aggregate status for a set of Fulfillment statuses, or {@code null} when there is
     * nothing to derive (no Fulfillment). {@code paid} is the money condition for COMPLETED.
     */
    public static OrderStatus derive(Collection<OrderFulfillmentStatus> statuses, boolean paid) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        if (statuses.contains(OrderFulfillmentStatus.FAILED)) {
            return OrderStatus.NEEDS_ATTENTION;
        }
        List<OrderFulfillmentStatus> live = statuses.stream()
                .filter(s -> s != OrderFulfillmentStatus.CANCELLED).toList();
        if (live.isEmpty()) {
            return OrderStatus.CANCELLED;
        }
        if (live.stream().allMatch(s -> s == OrderFulfillmentStatus.COMPLETED)) {
            return paid ? OrderStatus.COMPLETED : OrderStatus.NEEDS_ATTENTION;
        }
        if (live.contains(OrderFulfillmentStatus.COMPLETED)) {
            return OrderStatus.PARTIALLY_COMPLETED;
        }
        if (live.contains(OrderFulfillmentStatus.SHIPPING)) {
            return OrderStatus.SHIPPING;
        }
        if (live.stream().allMatch(s -> s == OrderFulfillmentStatus.CONFIRMED)) {
            return OrderStatus.CONFIRMED;
        }
        return OrderStatus.PROCESSING;
    }

    /**
     * Whether the aggregator may write {@code target} over {@code current}. PENDING_PAYMENT, CANCELLED and COMPLETED
     * are never overwritten; NEEDS_ATTENTION is never cleared (and is the only protected-looking state that is kept
     * as it is); the only way into NEEDS_ATTENTION is a FAILED Fulfillment.
     */
    static boolean mayWrite(OrderStatus current, OrderStatus target) {
        if (current == null || target == null || current == target || target == OrderStatus.CANCELLED) {
            return false;
        }
        return switch (current) {
            case PENDING_PAYMENT, NEEDS_ATTENTION, CANCELLED, COMPLETED -> false;
            default -> true;
        };
    }

    /**
     * Recomputes and stores the Order status; writes {@code order_status_history} only when it changes. The caller
     * holds the Order write lock and has already changed (and flushed) the Fulfillment.
     *
     * @return the new status, or {@code null} when the Order was left as it was
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderStatus recompute(Order lockedOrder, User actor, String note) {
        Objects.requireNonNull(lockedOrder, "order");
        OrderStatus current = lockedOrder.getOrderStatus();
        List<OrderFulfillmentStatus> statuses = fulfillments.findStatusesByOrderId(lockedOrder.getId());
        boolean paid = lockedOrder.getPaymentStatus() == OrderPaymentStatus.PAID;
        OrderStatus target = derive(statuses, paid);
        if (!mayWrite(current, target)) {
            return null;
        }
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(lockedOrder);
        history.setOldStatus(current);
        history.setNewStatus(target);
        history.setChangedBy(actor);
        history.setNote(note != null && note.length() > 500 ? note.substring(0, 500) : note);
        lockedOrder.setOrderStatus(target);
        orders.saveAndFlush(lockedOrder);
        histories.save(history);
        if (target == OrderStatus.CANCELLED && promotionRelease != null) {
            // every part was undeliverable: the Order is cancelled, so its promotion use goes back
            promotionRelease.releaseIfCancelled(lockedOrder);
        }
        return target;
    }

    private com.oneshop.service.promotion.PromotionReleaseService promotionRelease;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPromotionRelease(com.oneshop.service.promotion.PromotionReleaseService promotionRelease) {
        this.promotionRelease = promotionRelease;
    }
}
