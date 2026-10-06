package com.oneshop.service.impl;

import com.oneshop.entity.Order;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.User;
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
    }

    private static void requireOnline(Order order) {
        if (order == null || order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new BadRequestException("Đơn hàng không sử dụng thanh toán trực tuyến.");
        }
    }
}
