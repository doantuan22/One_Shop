package com.oneshop.service.payment;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentRefund;
import com.oneshop.entity.PaymentRefundComponent;
import com.oneshop.entity.PaymentRefundStatus;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRefundRepository;
import com.oneshop.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Simulated, whole refund of money the gateway verifiably collected (BE-05 task D). Never a bank refund, never
 * partial, never per Fulfillment. The collected {@code SUCCESS} row is kept as evidence; the refund is recorded in its
 * own refund columns:
 *
 * <pre>
 * NONE -> PENDING_SIMULATION -> SIMULATED_REFUNDED     (orders.payment_status = REFUNDED)
 *                            -> SIMULATION_FAILED      (orders.payment_status unchanged; retry is safe)
 * </pre>
 *
 * <p>The caller holds the Order write lock, so two refunds of one Order serialize and the second finds
 * {@code SIMULATED_REFUNDED} and does nothing.
 */
@Component
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    public enum Result {
        /** at least one collected payment was refunded now */
        REFUNDED,
        /** everything collected was already refunded: no side effect */
        ALREADY_REFUNDED,
        /** the simulation failed; recorded as SIMULATION_FAILED, a retry is allowed */
        SIMULATION_FAILED,
        /** no verified ONLINE collection on this Order */
        NOT_APPLICABLE
    }

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final RefundSimulator simulator;
    private final PaymentRefundRepository ledger;

    public RefundService(PaymentRepository payments, OrderRepository orders, RefundSimulator simulator,
                         PaymentRefundRepository ledger) {
        this.payments = payments;
        this.orders = orders;
        this.simulator = simulator;
        this.ledger = ledger;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result simulate(Order lockedOrder, String reason) {
        List<Payment> collected = payments.findForLockedOrder(lockedOrder.getId()).stream()
                .filter(p -> p.getMethod() == PaymentMethod.ONLINE && p.getStatus() == PaymentStatus.SUCCESS)
                .toList();
        if (collected.isEmpty()) {
            return Result.NOT_APPLICABLE;
        }
        boolean refundedNow = false;
        boolean failed = false;
        for (Payment row : collected) {
            if (row.getRefundStatus() == PaymentRefundStatus.SIMULATED_REFUNDED) {
                continue;
            }
            if (ledger.findByPaymentId(row.getId()).stream().anyMatch(r -> r.getComponent() != PaymentRefundComponent.WHOLE_ORDER
                    && r.getStatus() != PaymentRefundStatus.SIMULATION_FAILED)) {
                // some of this money was already given back for a failed delivery: a second, whole refund would pay twice
                throw new BadRequestException("Khoản thanh toán đã được hoàn một phần; không thể hoàn toàn bộ lần nữa.");
            }
            LocalDateTime now = LocalDateTime.now().withNano(0);
            row.setRefundStatus(PaymentRefundStatus.PENDING_SIMULATION);
            row.setRefundRequestedAt(now);
            row.setRefundAmount(row.getAmount());
            row.setRefundReason(OrderStatusWriter.abbreviate(reason));
            payments.saveAndFlush(row);
            try {
                simulator.simulate(row);
                row.setRefundStatus(PaymentRefundStatus.SIMULATED_REFUNDED);
                row.setRefundedAt(LocalDateTime.now().withNano(0));
                refundedNow = true;
            } catch (RuntimeException ex) {
                log.warn("Simulated refund of payment {} failed: {}", row.getId(), ex.toString());
                row.setRefundStatus(PaymentRefundStatus.SIMULATION_FAILED);
                row.setRefundedAt(null);
                failed = true;
            }
            payments.saveAndFlush(row);
            writeLedger(lockedOrder, row);
        }
        if (!failed) {
            lockedOrder.setPaymentStatus(OrderPaymentStatus.REFUNDED);
            orders.saveAndFlush(lockedOrder);
        }
        if (failed) {
            return Result.SIMULATION_FAILED;
        }
        return refundedNow ? Result.REFUNDED : Result.ALREADY_REFUNDED;
    }

    /** Mirrors the whole-Order refund of a Payment in the refund ledger (one row per Payment, updated on a retry). */
    private void writeLedger(Order order, Payment row) {
        PaymentRefund entry = ledger.findByPaymentId(row.getId()).stream()
                .filter(r -> r.getComponent() == PaymentRefundComponent.WHOLE_ORDER).findFirst().orElseGet(() -> {
                    PaymentRefund created = new PaymentRefund();
                    created.setPayment(row);
                    created.setOrder(order);
                    created.setComponent(PaymentRefundComponent.WHOLE_ORDER);
                    created.setRequestedAt(row.getRefundRequestedAt());
                    return created;
                });
        entry.setAmount(row.getRefundAmount() == null ? row.getAmount() : row.getRefundAmount());
        entry.setStatus(row.getRefundStatus());
        entry.setReason(row.getRefundReason() == null ? "Hoàn tiền mô phỏng" : row.getRefundReason());
        entry.setRefundedAt(row.getRefundStatus() == PaymentRefundStatus.SIMULATED_REFUNDED ? row.getRefundedAt() : null);
        ledger.saveAndFlush(entry);
    }

    /** Collected ONLINE rows whose refund is not finished (for the Admin list and the retry). */
    public boolean hasUnfinishedRefund(Long orderId) {
        return payments.findByOrderIdOrderByIdAsc(orderId).stream().anyMatch(p ->
                p.getStatus() == PaymentStatus.SUCCESS && p.getMethod() == PaymentMethod.ONLINE
                        && (p.getRefundStatus() == PaymentRefundStatus.SIMULATION_FAILED
                        || p.getRefundStatus() == PaymentRefundStatus.PENDING_SIMULATION));
    }
}
