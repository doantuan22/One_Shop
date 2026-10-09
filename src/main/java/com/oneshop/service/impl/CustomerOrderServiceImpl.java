package com.oneshop.service.impl;

import com.oneshop.dto.response.*;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.entity.OrderStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.*;
import com.oneshop.service.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerOrderServiceImpl implements CustomerOrderService {
    private final OrderRepository orders;
    private final UserRepository users;
    private final OrderViewService views;
    private final OrderTransitionPolicy policy;
    private final OrderService stateMachine;
    private final InventoryService inventory;
    private final PaymentService payments;
    public CustomerOrderServiceImpl(OrderRepository orders, UserRepository users, OrderViewService views,
            OrderTransitionPolicy policy, OrderService stateMachine, InventoryService inventory, PaymentService payments) {
        this.orders = orders; this.users = users; this.views = views;
        this.policy = policy; this.stateMachine = stateMachine; this.inventory = inventory; this.payments = payments;
    }
    private User requireCustomer(String email) {
        return users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.CUSTOMER)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản khách hàng không hợp lệ."));
    }
    public List<OrderViewResponse> getOrders(String email) {
        requireCustomer(email);
        return orders.findByUserEmailOrderByCreatedAtDescIdDesc(email).stream().map(o -> views.summary(o, false, null)).toList();
    }
    public OrderDetailResponse getOrder(String email, Long id) {
        requireCustomer(email);
        var order = orders.findByIdAndUserEmail(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        return views.detail(order, true, null);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER') and #email == authentication.name")
    public void cancelOrder(String email, Long id) {
        User customer = requireCustomer(email);
        if (id == null) throw new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
        // PK-only Order lock, identical lock order to payment/fulfillment. Scope is checked before child reads/writes.
        var order = orders.findByIdForUpdate(id)
                .filter(o -> o.getUser().getId().equals(customer.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        if (order.getOrderStatus() == OrderStatus.CANCELLED) return;
        if (!policy.canCustomerCancel(order.getOrderStatus(), order.getFulfillmentType(),
                order.getPaymentMethod(), order.getPaymentStatus())) {
            throw new BadRequestException("Chỉ hủy đơn chưa thanh toán, trước khi cửa hàng chuẩn bị hàng.");
        }
        payments.closePendingForCustomerCancellation(order);
        inventory.restoreForCancelledOrder(order, "Hoàn tồn do khách hủy đơn - Order #" + id);
        stateMachine.cancelByCustomer(order, customer);
    }
}
