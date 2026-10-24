package com.oneshop.service.payment;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.entity.User;
import com.oneshop.exception.PaymentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.PaymentDeadlinePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The one engine that turns what the gateway says into the state of an ONLINE Order (BE-05 tasks A, B, F). Every
 * entry point takes the Order write lock first, re-reads the payment rows and decides there; nothing is decided from
 * the browser, from the scheduler's moment, or from state read before the lock.
 *
 * <p>Decision table (deadline = {@code created_at + 15 min}, reconciliation deadline = deadline + 15 min):
 *
 * <pre>
 * verification   Order PENDING_PAYMENT / NEEDS_ATTENTION (payment case)
 * SUCCESS < deadline  -> CONFIRMED, PAID                              (no stock change)
 * SUCCESS >= deadline -> CANCELLED + restore + simulated refund       (never confirmed)
 * FAILED              -> CANCELLED + restore, payment_status FAILED   (terminal, no retry on the same Order)
 * NOT_PAID            -> before deadline: wait; at/after: CANCELLED + restore
 * UNKNOWN             -> before deadline: wait; inside the window: reconciling (nothing changes);
 *                        at the end of the window: NEEDS_ATTENTION (stock stays deducted)
 * Order CANCELLED + money collected   -> recorded once, simulated refund once, Order untouched
 * </pre>
 */
@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);

    /** What a person's browser asked the demo gateway to enact (never a payment status). */
    public enum SimulatedResult { SUCCESS, FAILED, UNKNOWN }

    public enum Trigger { CUSTOMER_CALLBACK, TIMEOUT_SCAN, CUSTOMER_CANCEL, ADMIN_RECHECK, ADMIN_GATEWAY }

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final PaymentDeadlinePolicy deadlines;
    private final OrderStatusWriter writer;
    private final OrderCancellationService cancellation;
    private final RefundService refunds;

    public PaymentReconciliationService(OrderRepository orders, PaymentRepository payments, PaymentGateway gateway,
                                        PaymentDeadlinePolicy deadlines, OrderStatusWriter writer,
                                        OrderCancellationService cancellation, RefundService refunds) {
        this.orders = orders;
        this.payments = payments;
        this.gateway = gateway;
        this.deadlines = deadlines;
        this.writer = writer;
        this.cancellation = cancellation;
        this.refunds = refunds;
    }

    // ------------------------------------------------------------------ customer simulation (gateway callback)

    /**
     * The demo gateway enacts a scenario for the customer's own attempt and the backend verifies the ledger. The
     * browser chose a scenario, not a result: {@code paid_at}, the amount and the status come from the server.
     */
    @Transactional
    public PaymentOutcome simulate(String customerEmail, Long orderId, Long paymentId, SimulatedResult scenario) {
        if (!gateway.enabled()) {
            throw new PaymentConflictException(PaymentConflictException.GATEWAY_DISABLED,
                    "Cổng thanh toán không được bật trong môi trường này.");
        }
        Order order = lockOwnOrder(customerEmail, orderId);
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new PaymentConflictException(PaymentConflictException.NOT_A_PAYMENT_CASE,
                    "Đơn hàng này không sử dụng thanh toán trực tuyến.");
        }
        Payment attempt = payments.findByIdAndOrderId(paymentId, orderId)
                .filter(p -> p.getMethod() == PaymentMethod.ONLINE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần thanh toán của đơn hàng này."));
        requireMatchingCollection(order, attempt);
        LocalDateTime now = deadlines.now().withNano(0);
        LedgerState state = MockLedger.stateOf(attempt);

        if (state == LedgerState.SETTLED) {
            return PaymentOutcome.ALREADY_PAID; // duplicate callback: no second SUCCESS, no history
        }
        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.CANCELLED) {
            if (scenario == SimulatedResult.SUCCESS) {
                return recordLateCollection(order, attempt, now, null, Trigger.CUSTOMER_CALLBACK);
            }
            return PaymentOutcome.ALREADY_CANCELLED;
        }
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.NEEDS_ATTENTION) {
            throw new PaymentConflictException(PaymentConflictException.PAYMENT_NOT_PAYABLE,
                    "Đơn hàng không còn chờ thanh toán trực tuyến.");
        }
        switch (state) {
            case IN_DOUBT -> throw new PaymentConflictException(PaymentConflictException.PAYMENT_RECONCILING,
                    "Kết quả thanh toán đang được đối soát; chưa thể thanh toán lại.");
            case AWAITING -> {
                switch (scenario) {
                    case SUCCESS -> gateway.capture(attempt, now);
                    case FAILED -> gateway.decline(attempt);
                    case UNKNOWN -> gateway.markInDoubt(attempt, now);
                }
                payments.saveAndFlush(attempt);
            }
            default -> { /* CAPTURED / DECLINED: the ledger already holds the answer; apply it below */ }
        }
        PaymentOutcome outcome = reconcileLocked(order, Trigger.CUSTOMER_CALLBACK, null);
        if (outcome == PaymentOutcome.AWAITING && MockLedger.stateOf(attempt) == LedgerState.IN_DOUBT) {
            return PaymentOutcome.IN_DOUBT_RECORDED;
        }
        return outcome;
    }

    // ------------------------------------------------------------------ system / admin entry

    /** Re-verifies one Order under its lock (timeout worker, restart recovery). */
    @Transactional
    public PaymentOutcome reconcile(Long orderId, Trigger trigger) {
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null) {
            return PaymentOutcome.NOT_APPLICABLE;
        }
        return reconcileLocked(order, trigger, null);
    }

    // ------------------------------------------------------------------ the engine

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentOutcome reconcileLocked(Order order, Trigger trigger, User actor) {
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            return PaymentOutcome.NOT_APPLICABLE;
        }
        OrderStatus status = order.getOrderStatus();
        List<Payment> rows = payments.findForLockedOrder(order.getId());
        GatewayVerification verification = verify(order, rows);
        LocalDateTime now = deadlines.now();
        LocalDateTime deadline = deadlines.paymentDeadline(order);
        LocalDateTime reconciliationEnd = deadlines.reconciliationDeadline(order);

        if (status == OrderStatus.CANCELLED) {
            return lateOnCancelled(order, rows, verification, actor, trigger);
        }
        boolean paymentCase = status == OrderStatus.PENDING_PAYMENT
                || (status == OrderStatus.NEEDS_ATTENTION && order.getPaymentStatus() == OrderPaymentStatus.UNPAID);
        if (!paymentCase) {
            return order.getPaymentStatus() == OrderPaymentStatus.PAID ? PaymentOutcome.ALREADY_PAID
                    : PaymentOutcome.NOT_APPLICABLE;
        }

        switch (verification.result()) {
            case SUCCESS -> {
                Payment row = rowOf(rows, verification.reference());
                LocalDateTime at = verification.capturedAt();
                if (at != null && at.isBefore(deadline)) {
                    return confirm(order, row, at, actor, trigger, verification);
                }
                return cancelForLatePayment(order, row, at, actor, trigger, verification, deadline);
            }
            case FAILED -> {
                cancellation.cancelAndRestore(order,
                        "Hoàn tồn do thanh toán ONLINE thất bại - Order #" + order.getId(),
                        note("PAYMENT_FAILED", trigger, "ref", verification.reference(),
                                "kết quả", "cổng xác thực thất bại, không thu tiền"),
                        actor, OrderPaymentStatus.FAILED);
                return PaymentOutcome.CANCELLED_PAYMENT_FAILED;
            }
            case NOT_PAID -> {
                if (now.isBefore(deadline)) {
                    return PaymentOutcome.AWAITING;
                }
                cancellation.cancelAndRestore(order,
                        "Hoàn tồn do quá hạn thanh toán ONLINE - Order #" + order.getId(),
                        note("PAYMENT_TIMEOUT", trigger, "ref", verification.reference(),
                                "kết quả", "hết hạn, cổng xác nhận chưa thu tiền", "hạn", deadline),
                        actor, OrderPaymentStatus.FAILED);
                return PaymentOutcome.CANCELLED_TIMEOUT;
            }
            default -> { // UNKNOWN
                if (now.isBefore(deadline)) {
                    return PaymentOutcome.AWAITING;
                }
                if (now.isBefore(reconciliationEnd)) {
                    return PaymentOutcome.RECONCILING;
                }
                if (status == OrderStatus.PENDING_PAYMENT) {
                    writer.write(order, OrderStatus.NEEDS_ATTENTION, actor,
                            note("RECONCILIATION_TIMEOUT", trigger, "ref", verification.reference(),
                                    "kết quả", "UNKNOWN sau cửa sổ đối soát", "hạn", deadline,
                                    "đối soát đến", reconciliationEnd));
                    return PaymentOutcome.NEEDS_ATTENTION;
                }
                return PaymentOutcome.NEEDS_ATTENTION; // already waiting for an administrator
            }
        }
    }

    // ------------------------------------------------------------------ outcomes

    private PaymentOutcome confirm(Order order, Payment row, LocalDateTime at, User actor, Trigger trigger,
                                   GatewayVerification verification) {
        requireMatchingCollection(order, row);
        if (row != null) {
            row.setStatus(PaymentStatus.SUCCESS);
            row.setPaidAt(at.withNano(0));
            row.setTransactionCode(MockLedger.reference(row.getTransactionCode()));
            payments.saveAndFlush(row);
        }
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        writer.write(order, OrderStatus.CONFIRMED, actor,
                note("PAYMENT_CONFIRMED", trigger, "ref", verification.reference(),
                        "thu lúc", at, "kết quả", "đã thu trước hạn"));
        return PaymentOutcome.CONFIRMED;
    }

    private static void requireMatchingCollection(Order order, Payment row) {
        if (row == null || row.getOrder() == null || !order.getId().equals(row.getOrder().getId())
                || row.getMethod() != PaymentMethod.ONLINE || row.getAmount() == null
                || row.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new BadRequestException("Bằng chứng thanh toán không khớp đơn hàng, phương thức hoặc số tiền.");
        }
    }

    private PaymentOutcome cancelForLatePayment(Order order, Payment row, LocalDateTime at, User actor,
                                                Trigger trigger, GatewayVerification verification,
                                                LocalDateTime deadline) {
        if (row != null) {
            row.setStatus(PaymentStatus.SUCCESS); // the money is collected: keep it as evidence, never confirm
            row.setPaidAt(at == null ? deadlines.now().withNano(0) : at.withNano(0));
            row.setTransactionCode(MockLedger.reference(row.getTransactionCode()));
            payments.saveAndFlush(row);
        }
        cancellation.cancelAndRestore(order,
                "Hoàn tồn do thanh toán ONLINE sau hạn - Order #" + order.getId(),
                note("LATE_PAYMENT", trigger, "ref", verification.reference(), "thu lúc", at, "hạn", deadline,
                        "kết quả", "thu sau hạn: hủy đơn, hoàn tiền"),
                actor, OrderPaymentStatus.PAID);
        refunds.simulate(order, "Khoản thu sau hạn thanh toán; hoàn tiền");
        return PaymentOutcome.CANCELLED_LATE_PAYMENT;
    }

    /** A collection that is visible on an Order that was cancelled: recorded and refunded once, never revived. */
    private PaymentOutcome lateOnCancelled(Order order, List<Payment> rows, GatewayVerification verification,
                                           User actor, Trigger trigger) {
        // a CAPTURED (unapplied) attempt on a cancelled Order cannot normally exist; handle it like a late collection
        Payment unapplied = rows.stream().filter(p -> MockLedger.stateOf(p) == LedgerState.CAPTURED).findFirst()
                .orElse(null);
        if (unapplied != null) {
            unapplied.setStatus(PaymentStatus.SUCCESS);
            unapplied.setTransactionCode(MockLedger.reference(unapplied.getTransactionCode()));
            payments.saveAndFlush(unapplied);
        }
        boolean collected = rows.stream().anyMatch(p -> p.getMethod() == PaymentMethod.ONLINE
                && p.getStatus() == PaymentStatus.SUCCESS);
        if (!collected) {
            return PaymentOutcome.ALREADY_CANCELLED;
        }
        if (order.getPaymentStatus() != OrderPaymentStatus.REFUNDED) {
            if (order.getPaymentStatus() != OrderPaymentStatus.PAID) {
                order.setPaymentStatus(OrderPaymentStatus.PAID);
                orders.saveAndFlush(order);
            }
            refunds.simulate(order, "Khoản thu trên đơn đã hủy; hoàn tiền");
            writer.audit(order, actor, note("LATE_PAYMENT", trigger, "ref", verification.reference(),
                    "kết quả", "đơn đã hủy, hoàn tiền"));
        }
        return PaymentOutcome.LATE_PAYMENT_REFUNDED;
    }

    private PaymentOutcome recordLateCollection(Order order, Payment attempt, LocalDateTime at, User actor,
                                                Trigger trigger) {
        String reference = MockLedger.reference(attempt.getTransactionCode());
        List<Payment> rows = payments.findForLockedOrder(order.getId());
        boolean recorded = rows.stream().anyMatch(p -> p.getStatus() == PaymentStatus.SUCCESS
                && reference.equals(MockLedger.reference(p.getTransactionCode())));
        if (!recorded) {
            Payment collected = new Payment();
            collected.setOrder(order);
            collected.setMethod(PaymentMethod.ONLINE);
            collected.setAmount(order.getTotalAmount());
            collected.setStatus(PaymentStatus.SUCCESS);
            collected.setTransactionCode(reference);
            collected.setPaidAt(at.withNano(0));
            payments.saveAndFlush(collected);
        }
        if (order.getPaymentStatus() != OrderPaymentStatus.REFUNDED) {
            order.setPaymentStatus(OrderPaymentStatus.PAID);
            orders.saveAndFlush(order);
            refunds.simulate(order, "Khoản thu đến sau khi đơn đã hủy; hoàn tiền");
            writer.audit(order, actor, note("LATE_PAYMENT", trigger, "ref", reference, "thu lúc", at,
                    "kết quả", "đơn đã hủy, ghi nhận một lần và hoàn tiền"));
        }
        return PaymentOutcome.LATE_PAYMENT_REFUNDED;
    }

    /** The gateway's current answer for an Order whose lock the caller holds. */
    @Transactional(propagation = Propagation.MANDATORY)
    public GatewayVerification currentVerification(Order lockedOrder) {
        return verify(lockedOrder, payments.findForLockedOrder(lockedOrder.getId()));
    }

    // ------------------------------------------------------------------ helpers

    /** A gateway that cannot answer is UNKNOWN, never FAILED (a timeout is not a decline). */
    private GatewayVerification verify(Order order, List<Payment> rows) {
        try {
            return gateway.verify(order, rows);
        } catch (RuntimeException ex) {
            log.warn("Gateway verification of order {} failed: {}", order.getId(), ex.toString());
            String reference = rows.isEmpty() ? null : MockLedger.reference(rows.get(0).getTransactionCode());
            return GatewayVerification.unknown(reference);
        }
    }

    private Order lockOwnOrder(String email, Long orderId) {
        // PK-only lock first, then ownership: no user join that would lock unrelated rows on SQL Server
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(PaymentReconciliationService::notFound);
        if (!order.getUser().getEmail().equals(email)) {
            throw notFound();
        }
        return order;
    }

    private static Payment rowOf(List<Payment> rows, String reference) {
        return rows.stream().filter(p -> reference != null
                && reference.equals(MockLedger.reference(p.getTransactionCode()))
                && p.getMethod() == PaymentMethod.ONLINE
                && (MockLedger.stateOf(p) == LedgerState.CAPTURED || MockLedger.stateOf(p) == LedgerState.SETTLED))
                .findFirst().orElse(null);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
    }

    /** {@code [TAG] nguồn=…; key=value; …} (the schema note is 500 characters; the writer abbreviates). */
    static String note(String tag, Trigger trigger, Object... pairs) {
        StringBuilder text = new StringBuilder("[").append(tag).append("] nguồn=").append(trigger);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                text.append("; ").append(pairs[i]).append('=').append(pairs[i + 1]);
            }
        }
        return text.toString();
    }
}
