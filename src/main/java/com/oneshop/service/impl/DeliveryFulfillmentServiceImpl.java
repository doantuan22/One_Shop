package com.oneshop.service.impl;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderItemRepository;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.DeliveryFulfillmentService;
import com.oneshop.service.OrderService;
import com.oneshop.service.PaymentService;
import com.oneshop.service.StoreService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/** Authorizes and orchestrates fixed DELIVERY actions; OrderService alone changes order_status/history. */
@Service
@Transactional(readOnly = true)
public class DeliveryFulfillmentServiceImpl implements DeliveryFulfillmentService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository histories;
    private final PaymentRepository paymentRepository;
    private final UserRepository users;
    private final StoreService stores;
    private final OrderService stateMachine;
    private final PaymentService payments;

    public DeliveryFulfillmentServiceImpl(OrderRepository orders, OrderItemRepository items,
                                         OrderStatusHistoryRepository histories, PaymentRepository paymentRepository,
                                         UserRepository users, StoreService stores, OrderService stateMachine,
                                         PaymentService payments) {
        this.orders = orders;
        this.items = items;
        this.histories = histories;
        this.paymentRepository = paymentRepository;
        this.users = users;
        this.stores = stores;
        this.stateMachine = stateMachine;
        this.payments = payments;
    }

    @Override
    public List<DeliveryOrderResponse> getDeliveryOrders(String staffEmail) {
        return orders.findByStoreIdInAndFulfillmentTypeOrderByCreatedAtDescIdDesc(assignedIds(staffEmail), FulfillmentType.DELIVERY)
                .stream().map(DeliveryFulfillmentServiceImpl::summary).toList();
    }

    @Override
    public DeliveryOrderDetailResponse getDeliveryOrder(String staffEmail, Long orderId) {
        Order order = orders.findByIdAndStoreIdIn(orderId, assignedIds(staffEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn giao hàng của chi nhánh."));
        if (order.getFulfillmentType() != FulfillmentType.DELIVERY) {
            throw new BadRequestException("Đơn hàng này không phải đơn giao tận nơi.");
        }
        return new DeliveryOrderDetailResponse(summary(order), items.findByOrderId(orderId).stream()
                .map(item -> new OrderItemResponse(item.getProductName(), item.getUnitPrice(), item.getQuantity(), item.getSubtotal())).toList(),
                histories.findByOrderIdOrderByChangedAtAscIdAsc(orderId).stream()
                        .map(entry -> new OrderHistoryResponse(entry.getOldStatus(), entry.getNewStatus(),
                                entry.getChangedBy() == null ? "Hệ thống" : entry.getChangedBy().getFullName(),
                                entry.getNote(), entry.getChangedAt())).toList(),
                paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                        .map(payment -> new PaymentResponse(payment.getId(), orderId, payment.getMethod(), payment.getAmount(),
                                payment.getStatus(), payment.getTransactionCode(), payment.getPaidAt(), payment.getCreatedAt())).toList());
    }

    @Override
    @Transactional
    public void startPreparingDelivery(String staffEmail, Long orderId) { perform(staffEmail, orderId, DeliveryAction.PREPARE); }

    @Override
    @Transactional
    public void markDeliveryPacked(String staffEmail, Long orderId) { perform(staffEmail, orderId, DeliveryAction.PACK); }

    @Override
    @Transactional
    public void markDeliveryShipping(String staffEmail, Long orderId) { perform(staffEmail, orderId, DeliveryAction.SHIP); }

    @Override
    @Transactional
    public void completeDelivery(String staffEmail, Long orderId) { perform(staffEmail, orderId, DeliveryAction.COMPLETE); }

    private void perform(String staffEmail, Long orderId, DeliveryAction action) {
        if (orderId == null) throw new BadRequestException("Thiếu mã đơn hàng.");
        // PK-only lock first, consistent with PaymentService. Never trust a browser storeId for authorization.
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        stores.requireAssignedStore(staffEmail, order.getStore().getId());
        User actor = users.findByEmail(staffEmail)
                .filter(user -> user.isActive() && user.getRole().getName() == RoleName.STAFF)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản nhân viên không hợp lệ."));
        if (order.getFulfillmentType() != FulfillmentType.DELIVERY) {
            throw new BadRequestException("Thao tác này chỉ dành cho đơn giao tận nơi.");
        }
        if (order.getPaymentMethod() != PaymentMethod.ONLINE && order.getPaymentMethod() != PaymentMethod.COD) {
            throw new BadRequestException("Đơn giao tận nơi chỉ hỗ trợ ONLINE hoặc COD.");
        }
        if (order.getOrderStatus() == OrderStatus.COMPLETED || order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Đơn hàng đã hoàn tất hoặc đã hủy; không thể tiếp tục giao hàng.");
        }
        if (order.getOrderStatus() != action.getExpectedStatus()) {
            throw new BadRequestException("Trạng thái đơn hàng đã thay đổi hoặc chưa phù hợp với thao tác. Vui lòng tải lại trang.");
        }
        if (!paymentAllowsFulfillment(order)) {
            throw new BadRequestException(order.getPaymentMethod() == PaymentMethod.ONLINE
                    ? "Đơn ONLINE chưa thanh toán thành công; không thể xử lý giao hàng."
                    : "Đơn COD đã thu tiền hoặc có trạng thái thanh toán không hợp lệ.");
        }
        if (action == DeliveryAction.COMPLETE && order.getPaymentMethod() == PaymentMethod.COD) {
            payments.recordCodCollected(order); // MANDATORY: Payment + PAID + completion/history share this transaction.
        }
        stateMachine.transition(orderId, action.getExpectedStatus(), action.getTargetStatus(), actor, action.getLabel());
    }

    private List<Long> assignedIds(String staffEmail) {
        var scope = stores.getAssignedStores(staffEmail);
        if (scope.isEmpty()) throw new AccessDeniedException("Tài khoản chưa được phân công chi nhánh hoạt động.");
        return scope.stream().map(StoreResponse::id).toList();
    }

    private static boolean paymentAllowsFulfillment(Order order) {
        return (order.getPaymentMethod() == PaymentMethod.ONLINE && order.getPaymentStatus() == OrderPaymentStatus.PAID)
                || (order.getPaymentMethod() == PaymentMethod.COD && order.getPaymentStatus() == OrderPaymentStatus.UNPAID);
    }

    private static DeliveryOrderResponse summary(Order order) {
        DeliveryAction next = order.getFulfillmentType() == FulfillmentType.DELIVERY && paymentAllowsFulfillment(order)
                ? Arrays.stream(DeliveryAction.values()).filter(action -> action.getExpectedStatus() == order.getOrderStatus()).findFirst().orElse(null)
                : null;
        return new DeliveryOrderResponse(order.getId(), order.getStore().getId(), order.getStore().getName(), order.getCreatedAt(),
                order.getFulfillmentType(), order.getPaymentMethod(), order.getPaymentStatus(), order.getOrderStatus(),
                order.getReceiverName(), order.getReceiverPhone(), order.getShippingAddress(), order.getTotalAmount(), next);
    }
}
