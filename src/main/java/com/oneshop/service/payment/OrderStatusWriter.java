package com.oneshop.service.payment;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * The only writer of the payment-domain Order transitions (BE-05). It enforces the allowed edges, writes the status
 * and its history row (actor + note) in the caller's transaction, and never skips the history. The caller holds the
 * Order write lock.
 *
 * <pre>
 * PENDING_PAYMENT -> CONFIRMED | CANCELLED | NEEDS_ATTENTION
 * NEEDS_ATTENTION -> CONFIRMED | CANCELLED     (Admin or a conclusive reconciliation result)
 * CONFIRMED       -> CANCELLED                 (customer cancel before PREPARING)
 * CANCELLED       -> (nothing: an Order is never revived)
 * </pre>
 */
@Component
public class OrderStatusWriter {

    static final int NOTE_MAX = 500;

    private static final Map<OrderStatus, Set<OrderStatus>> EDGES = Map.of(
            OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.NEEDS_ATTENTION),
            OrderStatus.NEEDS_ATTENTION, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.CANCELLED));

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository histories;

    public OrderStatusWriter(OrderRepository orders, OrderStatusHistoryRepository histories) {
        this.orders = orders;
        this.histories = histories;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(Order lockedOrder, OrderStatus target, User actor, String note) {
        OrderStatus current = lockedOrder.getOrderStatus();
        if (!EDGES.getOrDefault(current, Set.of()).contains(target)) {
            throw new BadRequestException("Chuyển trạng thái đơn hàng không hợp lệ: " + current + " → " + target + ".");
        }
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(lockedOrder);
        history.setOldStatus(current);
        history.setNewStatus(target);
        history.setChangedBy(actor);
        history.setNote(abbreviate(note));
        lockedOrder.setOrderStatus(target);
        orders.saveAndFlush(lockedOrder);
        histories.save(history);
    }

    /** An audit row for an action that changed no status (for example an Admin re-check that found nothing new). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void audit(Order lockedOrder, User actor, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(lockedOrder);
        history.setOldStatus(lockedOrder.getOrderStatus());
        history.setNewStatus(lockedOrder.getOrderStatus());
        history.setChangedBy(actor);
        history.setNote(abbreviate(note));
        histories.save(history);
    }

    /** {@code note} is 500 characters in the schema; evidence beyond that is abbreviated, never an error. */
    static String abbreviate(String note) {
        if (note == null) {
            return null;
        }
        return note.length() <= NOTE_MAX ? note : note.substring(0, NOTE_MAX - 1) + "…";
    }
}
