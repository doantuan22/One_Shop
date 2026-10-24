package com.oneshop.service.payment;

import com.oneshop.dto.response.AdminPaymentAttemptResponse;
import com.oneshop.dto.response.AdminPaymentCaseDetailResponse;
import com.oneshop.dto.response.AdminPaymentCaseResponse;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentRefundStatus;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.PaymentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRefundRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.OrderViewService;
import com.oneshop.service.PaymentDeadlinePolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrator handling of ONLINE payment cases (BE-05 task E, DEC-19). Authority is the ADMIN role checked here
 * (and by the URL rule); the actor is the authenticated account, never a request field.
 *
 * <p>What an administrator can do is bounded by evidence from the gateway ledger, not by a status choice:
 * <ul>
 *   <li><b>recheck</b>: re-run the reconciliation. A collection verified before the deadline confirms the Order; a
 *       verified decline or no collection cancels and restores once; a collection after the deadline (or on a
 *       cancelled Order) cancels, restores once and simulates one refund; still unknown changes nothing but is
 *       recorded;</li>
 *   <li><b>settle the gateway</b> (mock operator answer for an in-doubt attempt: collected / not collected) followed by
 *       the same reconciliation;</li>
 *   <li><b>retry the simulated refund</b> of money that was collected, after a failed simulation.</li>
 * </ul>
 * There is no way to set an arbitrary status, to mark PAID without ledger evidence, to edit stock, to create an
 * inventory movement, to revive a cancelled Order or to refund part of an amount.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentService {

    public static final int PAGE_SIZE = 20;

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderFulfillmentRepository fulfillments;
    private final UserRepository users;
    private final PaymentGateway gateway;
    private final PaymentReconciliationService reconciliation;
    private final RefundService refunds;
    private final PartialRefundService partialRefunds;
    private final PaymentRefundRepository refundLedger;
    private final OrderStatusWriter writer;
    private final PaymentDeadlinePolicy deadlines;
    private final OrderViewService views;

    public AdminPaymentService(OrderRepository orders, PaymentRepository payments,
                               OrderFulfillmentRepository fulfillments, UserRepository users, PaymentGateway gateway,
                               PaymentReconciliationService reconciliation, RefundService refunds,
                               OrderStatusWriter writer, PaymentDeadlinePolicy deadlines, OrderViewService views,
                               PartialRefundService partialRefunds, PaymentRefundRepository refundLedger) {
        this.partialRefunds = partialRefunds;
        this.refundLedger = refundLedger;
        this.orders = orders;
        this.payments = payments;
        this.fulfillments = fulfillments;
        this.users = users;
        this.gateway = gateway;
        this.reconciliation = reconciliation;
        this.refunds = refunds;
        this.writer = writer;
        this.deadlines = deadlines;
        this.views = views;
    }

    // ------------------------------------------------------------------ reads

    /** The cases that need an Administrator (the default view of the console). */
    @Transactional(readOnly = true)
    public Page<AdminPaymentCaseResponse> getCases(int page) {
        return getCases(PaymentCaseCategory.ATTENTION, null, page);
    }

    /**
     * One filter of the console. {@code view == null} lists every ONLINE Order. {@code search} (optional) matches an
     * Order id exactly or a customer name / e-mail. Nothing is written: listing never changes a payment state.
     */
    @Transactional(readOnly = true)
    public Page<AdminPaymentCaseResponse> getCases(PaymentCaseCategory view, String search, int page) {
        return getCases(view, search, null, null, page);
    }

    /**
     * Console with an optional createdAt range ({@code from} inclusive, {@code to} inclusive — the query excludes the
     * first instant after). Nothing is written: listing never changes a payment state.
     */
    @Transactional(readOnly = true)
    public Page<AdminPaymentCaseResponse> getCases(PaymentCaseCategory view, String search,
                                                   java.time.LocalDate from, java.time.LocalDate to, int page) {
        var range = com.oneshop.service.query.SqlDayRange.of(from, to);
        String q = search == null ? "" : search.trim();
        if (q.length() > 100) {
            q = q.substring(0, 100);
        }
        var words = q.matches("\\d+") ? java.util.List.<String>of() : com.oneshop.service.search.TextSearch.words(q);
        if (!words.isEmpty()) {
            var matched = orders.searchPaymentCases(view == null ? "all" : view.key(), "", "%%", deadlines.expiredBefore(),
                            range.fromInclusive(), range.toExclusive(),
                            org.springframework.data.domain.Pageable.unpaged())
                    .getContent().stream()
                    .filter(o -> com.oneshop.service.search.TextSearch.matchesAll(words, o.getUser().getFullName(), o.getUser().getEmail()))
                    .toList();
            return com.oneshop.service.search.TextSearch.slice(matched, com.oneshop.service.query.Paging.page(page, PAGE_SIZE)).map(this::summary);
        }
        String like = "%" + q.toLowerCase(java.util.Locale.ROOT).replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        return orders.searchPaymentCases(view == null ? "all" : view.key(), q, like, deadlines.expiredBefore(),
                range.fromInclusive(), range.toExclusive(),
                com.oneshop.service.query.Paging.page(page, PAGE_SIZE)).map(this::summary);
    }

    /** Size of every filter, for the tabs of the console (same rules as the list and as the Dashboard). */
    @Transactional(readOnly = true)
    public Map<String, Long> getCaseCounts() {
        var cutoff = deadlines.expiredBefore();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", orders.countPaymentCases("all", cutoff));
        for (PaymentCaseCategory category : PaymentCaseCategory.values()) {
            if (category != PaymentCaseCategory.OTHER) {
                counts.put(category.key(), orders.countPaymentCases(category.key(), cutoff));
            }
        }
        return counts;
    }

    /** Java twin of the SQL filters in {@code OrderRepository}; a database test keeps the two equal. */
    PaymentCaseCategory classify(Order order, List<Payment> rows) {
        var cutoff = deadlines.expiredBefore();
        boolean pending = order.getOrderStatus() == OrderStatus.PENDING_PAYMENT;
        boolean refundOpen = rows.stream().anyMatch(p -> p.getStatus() == PaymentStatus.SUCCESS
                && (p.getRefundStatus() == PaymentRefundStatus.SIMULATION_FAILED
                || p.getRefundStatus() == PaymentRefundStatus.PENDING_SIMULATION));
        boolean overdue = pending && !order.getCreatedAt().isAfter(cutoff);
        refundOpen = refundOpen || ledgerOpen(order.getId());
        if (order.getOrderStatus() == OrderStatus.NEEDS_ATTENTION || overdue || refundOpen) {
            return PaymentCaseCategory.ATTENTION;
        }
        if (pending) {
            boolean inDoubt = rows.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PENDING
                    && MockLedger.inDoubt(p.getTransactionCode()));
            return inDoubt ? PaymentCaseCategory.RECONCILING : PaymentCaseCategory.WAITING;
        }
        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            return PaymentCaseCategory.PAID;
        }
        if (order.getPaymentStatus() == OrderPaymentStatus.REFUNDED) {
            return PaymentCaseCategory.REFUNDED;
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED && (order.getPaymentStatus() == OrderPaymentStatus.UNPAID
                || order.getPaymentStatus() == OrderPaymentStatus.FAILED)) {
            return PaymentCaseCategory.FAILED;
        }
        return PaymentCaseCategory.OTHER;
    }

    @Transactional(readOnly = true)
    public AdminPaymentCaseDetailResponse getCase(Long orderId) {
        Order order = requireOnline(orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng.")));
        List<Payment> rows = payments.findByOrderIdOrderByIdAsc(orderId);
        List<AdminPaymentAttemptResponse> attempts = rows.stream().filter(p -> p.getMethod() == PaymentMethod.ONLINE)
                .map(p -> new AdminPaymentAttemptResponse(p.getId(), p.getStatus(), MockLedger.stateOf(p),
                        MockLedger.reference(p.getTransactionCode()), p.getAmount(), p.getPaidAt(), p.getRefundStatus(),
                        p.getRefundAmount(), p.getRefundedAt(), p.getRefundReason(), p.getCreatedAt())).toList();
        Map<Long, OrderFulfillmentStatus> parts = new LinkedHashMap<>();
        for (OrderFulfillment part : fulfillments.findByOrderIdOrderByAssignedAtAscIdAsc(orderId)) {
            parts.put(part.getId(), part.getStatus());
        }
        OrderStatus status = order.getOrderStatus();
        boolean open = status == OrderStatus.PENDING_PAYMENT || status == OrderStatus.NEEDS_ATTENTION
                || status == OrderStatus.CANCELLED;
        boolean inDoubt = rows.stream().anyMatch(p -> MockLedger.stateOf(p) == LedgerState.IN_DOUBT);
        boolean refundOpen = rows.stream().anyMatch(p -> p.getStatus() == PaymentStatus.SUCCESS
                && p.getMethod() == PaymentMethod.ONLINE
                && (p.getRefundStatus() == PaymentRefundStatus.SIMULATION_FAILED
                || p.getRefundStatus() == PaymentRefundStatus.PENDING_SIMULATION));
        // a failed PART refund (OPEN-10.1) can be retried whatever the Order status is: the Order is not cancelled
        boolean partRefundOpen = !refundLedger.findByOrderIdAndStatus(orderId, PaymentRefundStatus.SIMULATION_FAILED).isEmpty()
                && refundLedger.findByOrderIdAndStatus(orderId, PaymentRefundStatus.SIMULATION_FAILED).stream()
                .anyMatch(r -> r.getComponent() != com.oneshop.entity.PaymentRefundComponent.WHOLE_ORDER);
        return new AdminPaymentCaseDetailResponse(summary(order), views.detail(order, false, null), attempts, parts,
                open, gateway.enabled() && inDoubt && status != OrderStatus.CANCELLED,
                (refundOpen && status == OrderStatus.CANCELLED) || partRefundOpen, gateway.enabled());
    }

    // ------------------------------------------------------------------ actions (all under the Order lock)

    @Transactional
    public PaymentOutcome recheck(Long orderId) {
        User admin = currentAdmin();
        Order order = lockOnline(orderId);
        PaymentOutcome outcome = reconciliation.reconcileLocked(order,
                PaymentReconciliationService.Trigger.ADMIN_RECHECK, admin);
        auditWhenUnchanged(order, admin, "ADMIN_RECHECK", outcome);
        return outcome;
    }

    /**
     * Mock operator answer for an in-doubt attempt, then the same reconciliation. Only while the mock gateway is
     * enabled; an attempt that is not in doubt is refused (an administrator cannot invent a collection).
     */
    @Transactional
    public PaymentOutcome settleGateway(Long orderId, boolean collected) {
        if (!gateway.enabled()) {
            throw new PaymentConflictException(PaymentConflictException.GATEWAY_DISABLED,
                    "Cổng thanh toán không được bật trong môi trường này.");
        }
        User admin = currentAdmin();
        Order order = lockOnline(orderId);
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new PaymentConflictException(PaymentConflictException.PAYMENT_ALREADY_FINAL,
                    "Đơn hàng đã hủy; không thể đổi kết quả cổng thanh toán.");
        }
        Payment doubtful = payments.findForLockedOrder(orderId).stream()
                .filter(p -> p.getMethod() == PaymentMethod.ONLINE && MockLedger.stateOf(p) == LedgerState.IN_DOUBT)
                .findFirst().orElseThrow(() -> new PaymentConflictException(
                        PaymentConflictException.NOT_A_PAYMENT_CASE,
                        "Không có lần thanh toán nào ở trạng thái chưa xác định."));
        gateway.settleInDoubt(doubtful, collected);
        payments.saveAndFlush(doubtful);
        PaymentOutcome outcome = reconciliation.reconcileLocked(order,
                PaymentReconciliationService.Trigger.ADMIN_GATEWAY, admin);
        auditWhenUnchanged(order, admin, collected ? "ADMIN_GATEWAY_COLLECTED" : "ADMIN_GATEWAY_NOT_COLLECTED", outcome);
        return outcome;
    }

    /** Retries a simulated refund that failed (or never finished). Idempotent: a finished refund does nothing. */
    @Transactional
    public RefundService.Result retryRefund(Long orderId) {
        User admin = currentAdmin();
        Order order = lockOnline(orderId);
        boolean partFailed = refundLedger.findByOrderIdAndStatus(orderId, PaymentRefundStatus.SIMULATION_FAILED).stream()
                .anyMatch(r -> r.getComponent() != com.oneshop.entity.PaymentRefundComponent.WHOLE_ORDER);
        if (partFailed) {
            // OPEN-10.1: retry only the ledger rows that failed; finished rows are never refunded again
            PartialRefundService.Status status = partialRefunds.retryFailed(order);
            writer.audit(order, admin, "[ADMIN_REFUND_RETRY] hoàn một phần, kết quả=" + status);
            return status == PartialRefundService.Status.SIMULATION_FAILED ? RefundService.Result.SIMULATION_FAILED
                    : RefundService.Result.REFUNDED;
        }
        if (order.getOrderStatus() != OrderStatus.CANCELLED) {
            // money is only given back for an Order that cannot continue; an active Order is never refunded
            throw new PaymentConflictException(PaymentConflictException.NOT_A_PAYMENT_CASE,
                    "Chỉ hoàn tiền cho đơn đã hủy.");
        }
        RefundService.Result result = refunds.simulate(order, "Admin thử lại hoàn tiền");
        if (result == RefundService.Result.NOT_APPLICABLE) {
            throw new PaymentConflictException(PaymentConflictException.NOTHING_TO_REFUND,
                    "Đơn hàng không có khoản thu trực tuyến nào để hoàn.");
        }
        writer.audit(order, admin, "[ADMIN_REFUND_RETRY] kết quả=" + result);
        return result;
    }

    // ------------------------------------------------------------------ helpers

    /** A refund of this Order is failed or unfinished in the ledger (whole or part). */
    private boolean ledgerOpen(Long orderId) {
        return !refundLedger.findByOrderIdAndStatus(orderId, PaymentRefundStatus.SIMULATION_FAILED).isEmpty()
                || !refundLedger.findByOrderIdAndStatus(orderId, PaymentRefundStatus.PENDING_SIMULATION).isEmpty();
    }

    private void auditWhenUnchanged(Order order, User admin, String tag, PaymentOutcome outcome) {
        switch (outcome) {
            case AWAITING, RECONCILING, NEEDS_ATTENTION, NOT_APPLICABLE, ALREADY_PAID, ALREADY_CANCELLED ->
                    writer.audit(order, admin, "[" + tag + "] kết quả=" + outcome + "; trạng thái giữ nguyên");
            default -> { /* the state change wrote its own history row with this administrator as the actor */ }
        }
    }

    private Order lockOnline(Long orderId) {
        return requireOnline(orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng.")));
    }

    private static Order requireOnline(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new PaymentConflictException(PaymentConflictException.NOT_A_PAYMENT_CASE,
                    "Đơn hàng này không sử dụng thanh toán trực tuyến.");
        }
        return order;
    }

    private User currentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Chỉ quản trị viên được xử lý thanh toán.");
        }
        return users.findByEmail(auth.getName())
                .filter(u -> u.isActive() && u.getRole().getName() == RoleName.ADMIN)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản quản trị không hợp lệ."));
    }

    private AdminPaymentCaseResponse summary(Order order) {
        List<Payment> rows = payments.findForLockedOrder(order.getId());
        GatewayVerification verification;
        try {
            verification = gateway.verify(order, rows);
        } catch (RuntimeException ex) {
            verification = GatewayVerification.unknown(null);
        }
        PaymentRefundStatus refund = rows.stream().filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .map(Payment::getRefundStatus).filter(s -> s != PaymentRefundStatus.NONE).findFirst()
                .orElse(PaymentRefundStatus.NONE);
        OrderPaymentStatus paymentStatus = order.getPaymentStatus();
        return new AdminPaymentCaseResponse(order.getId(), order.getUser().getFullName(), order.getUser().getEmail(),
                order.getOrderStatus(), paymentStatus, order.getTotalAmount(), order.getCreatedAt(),
                deadlines.paymentDeadline(order), deadlines.reconciliationDeadline(order), verification.result(),
                refund, classify(order, rows));
    }
}
