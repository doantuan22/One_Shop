package com.oneshop.service.impl;

import java.time.LocalDateTime;
import com.oneshop.repository.OrderFulfillmentStatusHistoryRepository;
import com.oneshop.repository.OrderFulfillmentRepository;
import com.oneshop.entity.OrderFulfillmentStatusHistory;
import com.oneshop.entity.OrderFulfillmentStatus;
import com.oneshop.entity.OrderFulfillment;
import org.springframework.beans.factory.annotation.Autowired;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.User;
import com.oneshop.entity.RoleName;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.service.OrderService;
import com.oneshop.service.OrderTransitionPolicy;
import com.oneshop.service.OrderTransitionPolicy.Cause;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Updates only order_status and its history; callers own authorization and all business side effects. */
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderTransitionPolicy policy;

    public OrderServiceImpl(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
                            OrderTransitionPolicy policy) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.policy = policy;
    }

    @Override
    @Transactional
    public void transition(Long orderId, OrderStatus expectedStatus, OrderStatus targetStatus, User actor, String note) {
        if (orderId == null) throw new BadRequestException("Thiếu mã đơn hàng.");
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        apply(order, expectedStatus, targetStatus, Cause.FULFILLMENT, actor, note);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmAfterOnlinePayment(Order order) {
        requireOnline(order);
        apply(order, OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, Cause.PAYMENT_SUCCESS, null,
                "Thanh toán ONLINE thành công");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelAfterOnlinePaymentFailure(Order order) {
        requireOnline(order);
        apply(order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, Cause.PAYMENT_FAILURE, null,
                "Thanh toán ONLINE thất bại; hoàn tồn kho");
    }

    private void apply(Order order, OrderStatus expected, OrderStatus target, Cause cause, User actor, String note) {
        if (expected == null || order.getOrderStatus() != expected
                || !policy.canTransition(order.getOrderStatus(), target, order.getFulfillmentType(), cause)) {
            throw new BadRequestException("Chuyển trạng thái đơn hàng không hợp lệ hoặc trạng thái đã thay đổi.");
        }
        writeStatusAndHistory(order, target, actor, note);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelByCustomer(Order order, User customer) {
        if (order == null || customer == null || !customer.isActive() || customer.getRole().getName() != RoleName.CUSTOMER
                || !customer.getId().equals(order.getUser().getId())
                || !policy.canCustomerCancel(order.getOrderStatus(), order.getFulfillmentType(),
                        order.getPaymentMethod(), order.getPaymentStatus())) {
            throw new BadRequestException("Đơn hàng không còn đủ điều kiện để khách hủy.");
        }
        writeStatusAndHistory(order, OrderStatus.CANCELLED, customer, "Khách hàng hủy đơn trước khi chuẩn bị hàng");
    }

    /**
     * Setter injection so the 3-argument constructor stays usable. A cancelled Order must not leave live
     * Fulfillments behind: they are cancelled in the same transaction. The complete cancellation workflow
     * (races, refund simulation, timeout) is BE-05; this keeps the existing primitives consistent meanwhile.
     */
    private OrderFulfillmentRepository fulfillmentRepository;
    private OrderFulfillmentStatusHistoryRepository fulfillmentHistoryRepository;

    @Autowired
    void setFulfillmentRepositories(OrderFulfillmentRepository fulfillmentRepository,
                                    OrderFulfillmentStatusHistoryRepository fulfillmentHistoryRepository) {
        this.fulfillmentRepository = fulfillmentRepository;
        this.fulfillmentHistoryRepository = fulfillmentHistoryRepository;
    }

    private void cancelFulfillments(Order order, User actor, String note) {
        if (fulfillmentRepository == null || fulfillmentHistoryRepository == null || order.getId() == null) {
            return;
        }
        for (OrderFulfillment fulfillment : fulfillmentRepository.findByOrderId(order.getId())) {
            if (fulfillment.getStatus() == OrderFulfillmentStatus.CANCELLED) {
                continue;
            }
            if (fulfillment.getStatus() != OrderFulfillmentStatus.CONFIRMED) {
                // preparation has begun in some Store: the whole-Order cancellation must not go through
                throw new BadRequestException("Có phần hàng đã bắt đầu được chuẩn bị; không thể hủy đơn.");
            }
            OrderFulfillmentStatusHistory entry = new OrderFulfillmentStatusHistory();
            entry.setFulfillment(fulfillment);
            entry.setOldStatus(fulfillment.getStatus());
            entry.setNewStatus(OrderFulfillmentStatus.CANCELLED);
            entry.setChangedBy(actor);
            entry.setNote(note);
            fulfillment.setStatus(OrderFulfillmentStatus.CANCELLED);
            fulfillment.setCancelledAt(LocalDateTime.now().withNano(0));
            fulfillment.setCancelReason(note);
            fulfillmentHistoryRepository.save(entry);
        }
    }

    private void writeStatusAndHistory(Order order, OrderStatus target, User actor, String note) {
        if (note != null && note.length() > 500) {
            throw new BadRequestException("Ghi chú lịch sử không được vượt quá 500 ký tự.");
        }
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(order.getOrderStatus());
        history.setNewStatus(target);
        history.setChangedBy(actor);
        history.setNote(note);
        order.setOrderStatus(target);
        orderRepository.saveAndFlush(order);
        historyRepository.save(history); // Auditing supplies changed_at; commit/rollback is shared with the Order.
        if (target == OrderStatus.CANCELLED) {
            cancelFulfillments(order, actor, note);
        }
    }

    private static void requireOnline(Order order) {
        if (order == null || order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new BadRequestException("Đơn hàng không sử dụng thanh toán trực tuyến.");
        }
    }
}
