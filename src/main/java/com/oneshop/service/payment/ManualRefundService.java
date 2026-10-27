package com.oneshop.service.payment;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentRefund;
import com.oneshop.entity.PaymentRefundComponent;
import com.oneshop.entity.PaymentRefundStatus;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.PaymentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRefundRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Real (payOS) refunds are paid by a person from the bank; OneShop only keeps the request and the evidence.
 * An administrator who has actually transferred the money back records the bank transaction code here, and only that
 * turns a {@code MANUAL_REQUESTED} refund into {@code REFUNDED}. There is no automatic payout and no simulator.
 *
 * <p>The amount is the one that was requested (a goods, shipping, return or whole-Order amount decided by the existing
 * business rules); it cannot be edited here. The same bank transaction code can justify only one refund (unique index).
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class ManualRefundService {

    public enum Result { CONFIRMED, ALREADY_CONFIRMED }

    /** One real refund waiting for (or carrying) its evidence, for the Admin screen. */
    public record Line(Long refundId, PaymentRefundComponent component, BigDecimal amount, String reason,
                       LocalDateTime requestedAt, Long paymentId, Long providerOrderCode) {
    }

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentRefundRepository ledger;
    private final UserRepository users;
    private final OrderStatusWriter writer;

    public ManualRefundService(OrderRepository orders, PaymentRepository payments, PaymentRefundRepository ledger,
                               UserRepository users, OrderStatusWriter writer) {
        this.orders = orders;
        this.payments = payments;
        this.ledger = ledger;
        this.users = users;
        this.writer = writer;
    }

    /** Refunds of this Order that still have to be paid out of the bank. Read only. */
    @Transactional(readOnly = true)
    public List<Line> pending(Long orderId) {
        return ledger.findByOrderIdAndStatus(orderId, PaymentRefundStatus.MANUAL_REQUESTED).stream()
                .map(r -> new Line(r.getId(), r.getComponent(), r.getAmount(), r.getReason(), r.getRequestedAt(),
                        r.getPayment().getId(), r.getPayment().getProviderOrderCode())).toList();
    }

    @Transactional
    public Result confirm(Long orderId, Long refundId, String evidenceReference, String note) {
        User admin = currentAdmin();
        String evidence = evidenceReference == null ? "" : evidenceReference.trim();
        if (evidence.length() < 4 || evidence.length() > 100) {
            throw new BadRequestException("Mã giao dịch hoàn tiền thực tế phải từ 4 đến 100 ký tự.");
        }
        String reason = note == null ? null : note.trim();
        if (reason != null && reason.length() > 500) {
            throw new BadRequestException("Ghi chú tối đa 500 ký tự.");
        }
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        PaymentRefund refund = ledger.findById(refundId) // read after the Order lock
                .filter(r -> r.getOrder().getId().equals(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền của đơn hàng này."));
        if (refund.getStatus() == PaymentRefundStatus.REFUNDED) {
            if (evidence.equals(refund.getEvidenceReference())) {
                return Result.ALREADY_CONFIRMED; // a repeated submit
            }
            throw new PaymentConflictException(PaymentConflictException.PAYMENT_ALREADY_FINAL,
                    "Yêu cầu hoàn tiền này đã được xác nhận bằng một mã giao dịch khác.");
        }
        if (refund.getStatus() != PaymentRefundStatus.MANUAL_REQUESTED || !refund.getPayment().isPayos()) {
            throw new PaymentConflictException(PaymentConflictException.NOT_A_PAYMENT_CASE,
                    "Chỉ xác nhận các yêu cầu hoàn tiền thật đang chờ xử lý.");
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        refund.setStatus(PaymentRefundStatus.REFUNDED);
        refund.setRefundedAt(now);
        refund.setEvidenceReference(evidence);
        refund.setProcessedBy(admin);
        refund.setProcessedAt(now);
        refund.setProcessedNote(reason);
        try {
            ledger.saveAndFlush(refund);
        } catch (DataIntegrityViolationException ex) {
            throw new PaymentConflictException(PaymentConflictException.PAYMENT_ALREADY_FINAL,
                    "Mã giao dịch này đã được dùng để xác nhận một khoản hoàn khác.");
        }
        rollUp(order, refund.getPayment(), refund, now);
        writer.audit(order, admin, "[REFUND_CONFIRMED] hoàn " + refund.getAmount().toPlainString() + " đ, mã giao dịch="
                + evidence);
        return Result.CONFIRMED;
    }

    /** The Payment and the Order say REFUNDED only when all the money that must go back has really gone back. */
    private void rollUp(Order order, Payment payment, PaymentRefund confirmed, LocalDateTime now) {
        BigDecimal paidOut = ledger.sumByPaymentAndStatus(payment.getId(), PaymentRefundStatus.REFUNDED);
        boolean whole = confirmed.getComponent() == PaymentRefundComponent.WHOLE_ORDER;
        boolean allOut = whole || (payment.getStatus() == PaymentStatus.SUCCESS
                && paidOut.compareTo(payment.getAmount()) >= 0);
        if (!allOut) {
            return;
        }
        payment.setRefundStatus(PaymentRefundStatus.REFUNDED);
        payment.setRefundedAt(now);
        payments.saveAndFlush(payment);
        if (payment.getStatus() == PaymentStatus.SUCCESS && order.getPaymentStatus() != OrderPaymentStatus.REFUNDED) {
            order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
            orders.saveAndFlush(order);
        }
    }

    private User currentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Chỉ quản trị viên được xác nhận hoàn tiền.");
        }
        return users.findByEmail(auth.getName())
                .filter(u -> u.isActive() && u.getRole().getName() == RoleName.ADMIN)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản quản trị không hợp lệ."));
    }
}
