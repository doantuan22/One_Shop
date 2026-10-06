package com.oneshop.service.impl;

import com.oneshop.service.OrderService;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Only payment-driven transitions for Phase 9.1; fulfillment state machine belongs to Phase 9.2. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OrderServiceImpl implements OrderService {
    private final OrderStatusHistoryRepository historyRepository;

    public OrderServiceImpl(OrderStatusHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Override
    public void confirmAfterOnlinePayment(Order order) {
        transition(order, OrderStatus.CONFIRMED, OrderPaymentStatus.PAID, "Thanh toán ONLINE thành công");
    }

    @Override
    public void cancelAfterOnlinePaymentFailure(Order order) {
        transition(order, OrderStatus.CANCELLED, OrderPaymentStatus.FAILED, "Thanh toán ONLINE thất bại; hoàn tồn kho");
    }

    private void transition(Order order, OrderStatus next, OrderPaymentStatus paymentStatus, String note) {
        if (order.getPaymentMethod() != PaymentMethod.ONLINE
                || order.getOrderStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw new BadRequestException("Đơn hàng không còn chờ thanh toán trực tuyến.");
        }
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(order.getOrderStatus());
        history.setNewStatus(next);
        history.setChangedBy(null); // System transition, consistent with the Phase 8 creation entry.
        history.setNote(note);
        order.setOrderStatus(next);
        order.setPaymentStatus(paymentStatus);
        historyRepository.save(history);
    }
}
