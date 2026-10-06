package com.oneshop.service.impl;

import com.oneshop.dto.response.*;
import com.oneshop.entity.RoleName;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.*;
import com.oneshop.service.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerOrderServiceImpl implements CustomerOrderService {
    private final OrderRepository orders;
    private final UserRepository users;
    private final OrderViewService views;
    public CustomerOrderServiceImpl(OrderRepository orders, UserRepository users, OrderViewService views) {
        this.orders = orders; this.users = users; this.views = views;
    }
    private void requireCustomer(String email) {
        users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.CUSTOMER)
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
}
