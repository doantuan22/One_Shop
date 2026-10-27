package com.oneshop.service.payment;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.User;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.exception.PaymentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whole-Order cancellation by its owner (BE-05 task C). One transaction, Order lock first; the state is decided
 * after the lock and after any conclusive payment result has been applied, so a payment that arrived first wins and
 * a cancel that arrived first cannot be undone by a late callback.
 *
 * <ul>
 *   <li>{@code PENDING_PAYMENT}, nothing collected, no UNKNOWN: cancel + restore (payment stays UNPAID);</li>
 *   <li>{@code PENDING_PAYMENT} while the result is UNKNOWN: refused ({@code PAYMENT_RECONCILING});</li>
 *   <li>{@code CONFIRMED}, every part still CONFIRMED: cancel + restore; an ONLINE PAID Order also gets a
 *       simulated full refund;</li>
 *   <li>any part PREPARING or later: refused ({@code ORDER_ALREADY_PREPARING}), nothing changes;</li>
 *   <li>{@code NEEDS_ATTENTION}: refused until an administrator resolves it.</li>
 * </ul>
 */
@Service
public class CustomerOrderCancellationService {

    public enum Result {
        CANCELLED,
        /** cancelled and the paid ONLINE amount refunded (simulated) */
        CANCELLED_AND_REFUNDED,
        /** cancelled; a REAL refund is owed and waits for an administrator to pay and confirm it */
        CANCELLED_REFUND_REQUESTED,
        /** cancelled; the simulated refund failed and stays retryable by an administrator */
        CANCELLED_REFUND_FAILED,
        /** the Order was already cancelled (a repeated request, or a payment rule cancelled it first) */
        ALREADY_CANCELLED
    }

    private final OrderRepository orders;
    private final PaymentReconciliationService reconciliation;
    private final OrderCancellationService cancellation;
    private final RefundService refunds;

    public CustomerOrderCancellationService(OrderRepository orders, PaymentReconciliationService reconciliation,
                                            OrderCancellationService cancellation, RefundService refunds) {
        this.orders = orders;
        this.reconciliation = reconciliation;
        this.cancellation = cancellation;
        this.refunds = refunds;
    }

    @Transactional
    public Result cancel(User customer, Long orderId) {
        if (orderId == null) {
            throw notFound();
        }
        // PK-only lock first (same order as payment, fulfillment and timeout), ownership checked on the locked row
        Order order = orders.findByIdForUpdate(orderId)
                .filter(o -> o.getUser().getId().equals(customer.getId()))
                .orElseThrow(CustomerOrderCancellationService::notFound);
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            return Result.ALREADY_CANCELLED;
        }
        if (order.getPaymentMethod() == PaymentMethod.ONLINE) {
            // a conclusive gateway result that is waiting to be applied decides first (payment before cancel)
            reconciliation.reconcileLocked(order, PaymentReconciliationService.Trigger.CUSTOMER_CANCEL, null);
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                return Result.ALREADY_CANCELLED; // timeout / verified failure / late payment already cancelled it
            }
        }
        OrderStatus status = order.getOrderStatus();
        String stockNote = "Hoàn tồn do khách hủy đơn - Order #" + order.getId();
        String historyNote = "Khách hàng hủy đơn trước khi chuẩn bị hàng";

        if (status == OrderStatus.PENDING_PAYMENT) {
            if (order.getPaymentMethod() == PaymentMethod.ONLINE) {
                GatewayVerification verification = reconciliation.currentVerification(order);
                if (verification.result() == GatewayVerification.Result.UNKNOWN) {
                    throw new PaymentConflictException(PaymentConflictException.PAYMENT_RECONCILING,
                            "Kết quả thanh toán đang được đối soát nên chưa thể hủy đơn. Vui lòng thử lại sau.");
                }
            }
            cancellation.cancelAndRestore(order, stockNote, historyNote, customer, OrderPaymentStatus.UNPAID);
            return Result.CANCELLED;
        }
        if (status == OrderStatus.NEEDS_ATTENTION) {
            throw new FulfillmentConflictException(FulfillmentConflictException.ORDER_NEEDS_ATTENTION,
                    "Đơn hàng đang chờ quản trị viên xử lý nên chưa thể hủy.");
        }
        if (status != OrderStatus.CONFIRMED) {
            throw new FulfillmentConflictException(FulfillmentConflictException.ORDER_ALREADY_PREPARING,
                    "Cửa hàng đã bắt đầu xử lý đơn hàng nên không thể hủy.");
        }
        // CONFIRMED: every part is still CONFIRMED (the cancellation re-checks them under their locks)
        if (order.getPaymentMethod() == PaymentMethod.ONLINE) {
            if (order.getPaymentStatus() != OrderPaymentStatus.PAID) {
                throw new PaymentConflictException(PaymentConflictException.PAYMENT_NOT_PAYABLE,
                        "Trạng thái thanh toán của đơn hàng không hợp lệ để hủy.");
            }
            cancellation.cancelAndRestore(order, stockNote, historyNote, customer, OrderPaymentStatus.PAID);
            RefundService.Result refund = refunds.simulate(order,
                    "Khách hủy đơn trước khi chuẩn bị hàng; hoàn tiền toàn phần");
            return switch (refund) {
                case SIMULATION_FAILED -> Result.CANCELLED_REFUND_FAILED;
                case REFUND_REQUESTED, ALREADY_REQUESTED -> Result.CANCELLED_REFUND_REQUESTED;
                default -> Result.CANCELLED_AND_REFUNDED;
            };
        }
        if (order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw new PaymentConflictException(PaymentConflictException.PAYMENT_NOT_PAYABLE,
                    "Chỉ hủy đơn chưa thanh toán, trước khi cửa hàng chuẩn bị hàng.");
        }
        cancellation.cancelAndRestore(order, stockNote, historyNote, customer, OrderPaymentStatus.UNPAID);
        return Result.CANCELLED;
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
    }
}
