package com.oneshop.service.payment;

import com.oneshop.entity.InventoryMovementType;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderFulfillmentStatusHistory;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.repository.InventoryMovementRepository;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderFulfillmentStatusHistoryRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.InventoryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The single "cancel the whole Order and put the stock back" operation shared by the payment timeout, a verified
 * FAILED, a late collection, the customer's cancel and the Admin resolution (plan 5.3 / BE-05 task 7).
 *
 * <p>Inside the caller's transaction, under the Order lock: lock the Fulfillments (ascending id), refuse unless every
 * part is still {@code CONFIRMED}, restore each OrderItem to its own StoreProduct (ascending StoreProduct locks,
 * {@code CANCEL_ORDER} movements), cancel the parts with history, close the pending payment attempts as FAILED and
 * write the Order status with history. It happens only on the transition into CANCELLED; the guards make a second
 * execution impossible (status check, and a movement check against restoring twice).
 */
@Component
public class OrderCancellationService {

    private final OrderFulfillmentRepository fulfillments;
    private final OrderFulfillmentStatusHistoryRepository fulfillmentHistories;
    private final InventoryService inventory;
    private final InventoryMovementRepository movements;
    private final PaymentRepository payments;
    private final OrderStatusWriter statusWriter;

    public OrderCancellationService(OrderFulfillmentRepository fulfillments,
                                    OrderFulfillmentStatusHistoryRepository fulfillmentHistories,
                                    InventoryService inventory, InventoryMovementRepository movements,
                                    PaymentRepository payments, OrderStatusWriter statusWriter) {
        this.fulfillments = fulfillments;
        this.fulfillmentHistories = fulfillmentHistories;
        this.inventory = inventory;
        this.movements = movements;
        this.payments = payments;
        this.statusWriter = statusWriter;
    }

    /**
     * @param stockNote        movement note ({@code CANCEL_ORDER}); also the Fulfillment cancel reason
     * @param historyNote      note of the Order status history row
     * @param paymentStatusAfter the {@code orders.payment_status} to leave (UNPAID / FAILED / PAID; a refund changes
     *                           it to REFUNDED afterwards)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelAndRestore(Order lockedOrder, String stockNote, String historyNote, User actor,
                                 OrderPaymentStatus paymentStatusAfter) {
        OrderStatus status = lockedOrder.getOrderStatus();
        if (status == OrderStatus.CANCELLED) {
            throw new BadRequestException("Đơn hàng đã được hủy.");
        }
        List<OrderFulfillment> parts = fulfillments.findByOrderIdForUpdate(lockedOrder.getId());
        for (OrderFulfillment part : parts) {
            OrderFulfillmentStatus partStatus = part.getStatus();
            if (partStatus != OrderFulfillmentStatus.CONFIRMED && partStatus != OrderFulfillmentStatus.CANCELLED) {
                // preparation began (or a part failed after it): never cancel half an Order, never restock a part
                // that left the shelf
                throw new FulfillmentConflictException(FulfillmentConflictException.ORDER_ALREADY_PREPARING,
                        "Cửa hàng đã bắt đầu xử lý đơn hàng nên không thể hủy.");
            }
        }
        if (movements.hasRestorationForLockedOrder(lockedOrder.getId())) {
            throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                    "Tồn kho của đơn hàng đã được hoàn trước đó.");
        }

        inventory.restoreForCancelledOrder(lockedOrder, stockNote);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        for (OrderFulfillment part : parts) {
            if (part.getStatus() == OrderFulfillmentStatus.CANCELLED) {
                continue;
            }
            OrderFulfillmentStatusHistory entry = new OrderFulfillmentStatusHistory();
            entry.setFulfillment(part);
            entry.setOldStatus(part.getStatus());
            entry.setNewStatus(OrderFulfillmentStatus.CANCELLED);
            entry.setChangedBy(actor);
            entry.setNote(OrderStatusWriter.abbreviate(stockNote));
            part.setStatus(OrderFulfillmentStatus.CANCELLED);
            part.setCancelledAt(now);
            part.setCancelReason(OrderStatusWriter.abbreviate(stockNote));
            fulfillmentHistories.save(entry);
        }
        fulfillments.flush();

        // a still-pending attempt is closed as FAILED (the schema has no CANCELLED / EXPIRED); its gateway reference is
        // kept so that a collection that arrives later can still be matched to it
        for (Payment attempt : payments.findForLockedOrder(lockedOrder.getId())) {
            if (attempt.getStatus() == PaymentStatus.PENDING) {
                attempt.setStatus(PaymentStatus.FAILED);
                attempt.setPaidAt(null);
                attempt.setTransactionCode(MockLedger.reference(attempt.getTransactionCode()));
            }
        }
        payments.flush();

        lockedOrder.setPaymentStatus(paymentStatusAfter);
        statusWriter.write(lockedOrder, OrderStatus.CANCELLED, actor, historyNote);
    }
}
