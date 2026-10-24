package com.oneshop.service.payment;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderFulfillment;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderItem;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderReturn;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentRefund;
import com.oneshop.entity.PaymentRefundComponent;
import com.oneshop.entity.PaymentRefundStatus;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.entity.User;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRefundRepository;
import com.oneshop.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OPEN-10.1: SIMULATED refund of what a failed delivery leaves unpaid, recorded in the {@code payment_refunds} ledger
 * (never a bank movement, never a new Payment or Order).
 *
 * <p><b>Rule.</b> When a Fulfillment is confirmed undeliverable, the value of ITS goods is refunded. The one shipping fee
 * of the Order is refunded only when nothing at all is delivered (no Fulfillment COMPLETED and none left to deliver), and
 * only once: if any part was or will be delivered the shipping fee is kept. The total refunded can never exceed what
 * was collected, and when it equals it the Payment and the Order become REFUNDED.
 *
 * <p><b>Safety.</b> The caller holds the Order write lock, so refunds of one Order serialize. The database keeps one GOODS
 * refund per Fulfillment and one SHIPPING refund per Order (filtered unique indexes), so even a bug cannot refund the same
 * thing twice. A failed simulation is recorded as {@code SIMULATION_FAILED}, moves nothing and is retried explicitly.
 */
@Component
public class PartialRefundService {

    private static final Logger log = LoggerFactory.getLogger(PartialRefundService.class);

    public enum Status {
        /** nothing was collected online: there is nothing to refund */
        NOT_APPLICABLE,
        /** refunded now (goods, and the shipping fee when nothing was delivered) */
        REFUNDED,
        /** the simulation failed; recorded and retryable, nothing moved */
        SIMULATION_FAILED
    }

    /**
     * @param goods          simulated amount refunded now for the goods
     * @param shipping       simulated amount refunded now for the shipping fee ({@code 0} when it is kept)
     * @param fullyRefunded  everything collected has now been refunded: Payment and Order are REFUNDED
     */
    public record Result(Status status, BigDecimal goods, BigDecimal shipping, boolean fullyRefunded) {
        public BigDecimal total() {
            return goods.add(shipping);
        }
    }

    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final OrderItemRepository items;
    private final OrderRepository orders;
    private final RefundSimulator simulator;

    public PartialRefundService(PaymentRepository payments, PaymentRefundRepository refunds, OrderItemRepository items,
                                OrderRepository orders, RefundSimulator simulator) {
        this.payments = payments;
        this.refunds = refunds;
        this.items = items;
        this.orders = orders;
        this.simulator = simulator;
    }

    /** What would be refunded if {@code part} were confirmed undeliverable now (for the Admin screen). Read only. */
    public record Preview(BigDecimal goods, BigDecimal shipping) {
    }

    public Preview preview(Order order, OrderFulfillment part, List<OrderFulfillment> parts) {
        BigDecimal goods = goodsValue(part);
        return new Preview(goods, shippingRefundDue(order, part, parts) ? shippingFee(order) : BigDecimal.ZERO);
    }

    /**
     * Refunds the goods of {@code part} (already CANCELLED in memory) and, when nothing is delivered, the shipping fee.
     *
     * @param parts every Fulfillment of the Order with their CURRENT statuses
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Result refundUndeliverable(Order lockedOrder, OrderFulfillment part, List<OrderFulfillment> parts, User actor,
                                      String reason) {
        Payment collected = collectedPayment(lockedOrder);
        if (collected == null) {
            return new Result(Status.NOT_APPLICABLE, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        if (collected.getRefundStatus() == PaymentRefundStatus.SIMULATED_REFUNDED
                || lockedOrder.getPaymentStatus() == OrderPaymentStatus.REFUNDED) {
            throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                    "Khoản thanh toán của đơn đã được hoàn đủ trước đó; không thể hoàn thêm.");
        }
        BigDecimal goods = goodsValue(part);
        BigDecimal shipping = shippingRefundDue(lockedOrder, part, parts) ? shippingFee(lockedOrder) : BigDecimal.ZERO;
        BigDecimal remaining = collected.getAmount().subtract(refunds.sumCommitted(collected.getId()));
        // a part whose goods were fully covered by a promotion is worth 0: there is no goods row to refund then
        if (goods.signum() < 0 || goods.add(shipping).compareTo(remaining) > 0) {
            throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                    "Số tiền hoàn vượt quá số tiền còn có thể hoàn của khoản thanh toán; không thực hiện hoàn tiền.");
        }
        boolean failed = false;
        if (goods.signum() > 0) {
            failed |= !record(lockedOrder, collected, part, PaymentRefundComponent.GOODS, goods, reason, actor);
        }
        if (shipping.signum() > 0) {
            failed |= !record(lockedOrder, collected, null, PaymentRefundComponent.SHIPPING, shipping, reason, actor);
        }
        boolean full = !failed && closeWhenFullyRefunded(lockedOrder, collected, reason);
        return new Result(failed ? Status.SIMULATION_FAILED : Status.REFUNDED, goods, shipping, full);
    }

    /**
     * Post-delivery return approved by an Administrator: gives back the goods value of the returned Order in
     * simulation, recorded as ONE {@code RETURN} ledger row. The shipping fee is never part of it. Works for every
     * payment method, because a COMPLETED Order always has exactly one collected ({@code SUCCESS}) payment. A failed
     * simulation throws, so the caller's approval rolls back whole and can simply be repeated.
     *
     * @param lockedOrder the Order, locked by the caller
     * @param amount      goods value of the returned lines
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundReturn(Order lockedOrder, OrderReturn orderReturn, BigDecimal amount, User admin, String reason) {
        List<Payment> collected = payments.findForLockedOrder(lockedOrder.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS).toList();
        if (collected.size() != 1) {
            throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                    "Đơn hàng không có đúng một khoản thanh toán đã thu để hoàn tiền.");
        }
        Payment payment = collected.get(0);
        BigDecimal remaining = payment.getAmount().subtract(refunds.sumCommitted(payment.getId()));
        if (amount == null || amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                    "Số tiền hoàn vượt quá số tiền còn có thể hoàn của khoản thanh toán; không thực hiện hoàn tiền.");
        }
        PaymentRefund row = new PaymentRefund();
        row.setPayment(payment);
        row.setOrder(lockedOrder);
        row.setOrderReturn(orderReturn);
        row.setComponent(PaymentRefundComponent.RETURN);
        row.setAmount(amount);
        row.setStatus(PaymentRefundStatus.PENDING_SIMULATION);
        row.setReason(OrderStatusWriter.abbreviate(reason));
        row.setRequestedBy(admin);
        row.setRequestedAt(LocalDateTime.now().withNano(0));
        refunds.saveAndFlush(row);
        if (!simulate(row)) {
            throw new FulfillmentConflictException(FulfillmentConflictException.INCONSISTENT_FULFILLMENT,
                    "Hoàn tiền chưa thành công; yêu cầu trả hàng chưa được duyệt, vui lòng thử lại.");
        }
        closeWhenFullyRefunded(lockedOrder, payment, reason);
    }

    /** Retries the ledger rows whose simulation failed. Idempotent: finished rows are not touched. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Status retryFailed(Order lockedOrder) {
        List<PaymentRefund> failed = refunds.findByOrderIdAndStatus(lockedOrder.getId(), PaymentRefundStatus.SIMULATION_FAILED);
        if (failed.isEmpty()) {
            return Status.NOT_APPLICABLE;
        }
        boolean anyFailed = false;
        for (PaymentRefund row : failed) {
            row.setStatus(PaymentRefundStatus.PENDING_SIMULATION);
            refunds.saveAndFlush(row);
            anyFailed |= !simulate(row);
        }
        if (!anyFailed) {
            Payment collected = failed.get(0).getPayment();
            closeWhenFullyRefunded(lockedOrder, collected, failed.get(0).getReason());
        }
        return anyFailed ? Status.SIMULATION_FAILED : Status.REFUNDED;
    }

    // ------------------------------------------------------------------ rules

    /** The shipping fee is refunded when nothing is delivered: every other part is already CANCELLED. */
    static boolean shippingRefundDue(Order order, OrderFulfillment part, List<OrderFulfillment> parts) {
        return shippingFee(order).signum() > 0 && parts.stream().filter(p -> !p.getId().equals(part.getId()))
                .allMatch(p -> p.getStatus() == OrderFulfillmentStatus.CANCELLED);
    }

    private static BigDecimal shippingFee(Order order) {
        return order.getShippingFee() == null ? BigDecimal.ZERO : order.getShippingFee();
    }

    private BigDecimal goodsValue(OrderFulfillment part) {
        return items.findByFulfillmentIdInOrderByIdAsc(List.of(part.getId())).stream().map(OrderItem::getNetSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Payment collectedPayment(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            return null;
        }
        List<Payment> collected = payments.findForLockedOrder(order.getId()).stream()
                .filter(p -> p.getMethod() == PaymentMethod.ONLINE && p.getStatus() == PaymentStatus.SUCCESS).toList();
        return collected.size() == 1 ? collected.get(0) : null;
    }

    // ------------------------------------------------------------------ ledger

    private boolean record(Order order, Payment collected, OrderFulfillment part, PaymentRefundComponent component,
                           BigDecimal amount, String reason, User actor) {
        PaymentRefund row = new PaymentRefund();
        row.setPayment(collected);
        row.setOrder(order);
        row.setFulfillment(part);
        row.setComponent(component);
        row.setAmount(amount);
        row.setStatus(PaymentRefundStatus.PENDING_SIMULATION);
        row.setReason(OrderStatusWriter.abbreviate(reason));
        row.setRequestedBy(actor);
        row.setRequestedAt(LocalDateTime.now().withNano(0));
        refunds.saveAndFlush(row);
        return simulate(row);
    }

    private boolean simulate(PaymentRefund row) {
        try {
            simulator.simulate(row.getPayment());
            row.setStatus(PaymentRefundStatus.SIMULATED_REFUNDED);
            row.setRefundedAt(LocalDateTime.now().withNano(0));
            refunds.saveAndFlush(row);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Simulated refund {} of payment {} failed: {}", row.getId(), row.getPayment().getId(), ex.toString());
            row.setStatus(PaymentRefundStatus.SIMULATION_FAILED);
            row.setRefundedAt(null);
            refunds.saveAndFlush(row);
            return false;
        }
    }

    /** When the ledger gives back everything that was collected, the Payment and the Order say so. */
    private boolean closeWhenFullyRefunded(Order order, Payment collected, String reason) {
        BigDecimal committed = refunds.sumCommitted(collected.getId());
        if (committed.compareTo(collected.getAmount()) != 0) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        collected.setRefundStatus(PaymentRefundStatus.SIMULATED_REFUNDED);
        collected.setRefundAmount(collected.getAmount());
        collected.setRefundRequestedAt(collected.getRefundRequestedAt() == null ? now : collected.getRefundRequestedAt());
        collected.setRefundedAt(now);
        collected.setRefundReason(OrderStatusWriter.abbreviate(reason));
        payments.saveAndFlush(collected);
        order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
        orders.saveAndFlush(order);
        return true;
    }
}
