package com.oneshop.service.impl;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.exception.*;
import com.oneshop.repository.*;
import com.oneshop.service.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Pickup side effects and authorization surround the unchanged state/history foundation transaction. */
@Service
@Transactional(readOnly = true)
public class PickupFulfillmentServiceImpl implements PickupFulfillmentService {
    private final OrderRepository orders;
    private final UserRepository users;
    private final PaymentRepository receipts;
    private final StoreService stores;
    private final OrderService stateMachine;
    private final PaymentService payments;
    private final PickupCodeService codes;
    private final OrderViewService views;

    public PickupFulfillmentServiceImpl(OrderRepository orders, UserRepository users, PaymentRepository receipts,
            StoreService stores, OrderService stateMachine, PaymentService payments, PickupCodeService codes, OrderViewService views) {
        this.orders = orders; this.users = users; this.receipts = receipts; this.stores = stores;
        this.stateMachine = stateMachine; this.payments = payments; this.codes = codes; this.views = views;
    }
    public List<OrderViewResponse> getPickupOrders(String email) {
        return orders.findByStoreIdInAndFulfillmentTypeOrderByCreatedAtDescIdDesc(assignedIds(email), FulfillmentType.STORE_PICKUP)
                .stream().map(o -> views.summary(o, false, nextAction(o))).toList();
    }
    public OrderDetailResponse getPickupOrder(String email, Long id) {
        Order order = orders.findByIdAndStoreIdIn(id, assignedIds(email))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn nhận tại cửa hàng của chi nhánh."));
        requirePickup(order);
        return views.detail(order, false, nextAction(order));
    }
    @Transactional
    public void startPreparingPickup(String email, Long id) { perform(email, id, PickupAction.PREPARE, null); }
    @Transactional
    public void markReadyForPickup(String email, Long id) { perform(email, id, PickupAction.READY, null); }
    @Transactional
    public void completePickup(String email, Long id, String code) { perform(email, id, PickupAction.COMPLETE, code); }

    private void perform(String email, Long id, PickupAction action, String code) {
        if (id == null) throw new BadRequestException("Thiếu mã đơn hàng.");
        Order order = orders.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        stores.requireAssignedStore(email, order.getStore().getId());
        User actor = users.findByEmail(email).filter(u -> u.isActive() && u.getRole().getName() == RoleName.STAFF)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản nhân viên không hợp lệ."));
        requirePickup(order);
        if (order.getOrderStatus() != action.getExpectedStatus()) {
            throw new BadRequestException("Trạng thái đơn hàng đã thay đổi hoặc không phù hợp. Vui lòng tải lại trang.");
        }
        if (!paymentAllows(order)) throw new BadRequestException("Trạng thái thanh toán không hợp lệ để xử lý nhận tại cửa hàng.");
        if (order.getPaymentMethod() == PaymentMethod.PAY_AT_STORE
                && receipts.findFirstByOrderIdAndStatusOrderByIdAsc(id, PaymentStatus.SUCCESS).isPresent()) {
            throw new BadRequestException("Đơn hàng đã có thanh toán thành công; không thể thu tiền lần nữa.");
        }
        if (action == PickupAction.READY) {
            // Refuse inconsistent prior fields instead of silently rewriting a code/time.
            if (order.getPickupCode() != null || order.getReadyAt() != null || order.getPickedUpAt() != null)
                throw new BadRequestException("Thông tin nhận hàng không nhất quán.");
            order.setPickupCode(codes.generate());
            order.setReadyAt(LocalDateTime.now().withNano(0)); // Both fields MUST precede the foundation's READY flush.
        } else if (action == PickupAction.COMPLETE) {
            if (order.getReadyAt() == null || order.getPickedUpAt() != null)
                throw new BadRequestException("Thông tin nhận hàng không nhất quán.");
            codes.verify(code, order.getPickupCode()); // Wrong/malformed code changes nothing, including payment.
            if (order.getPaymentMethod() == PaymentMethod.PAY_AT_STORE) payments.recordPayAtStoreCollected(order);
            order.setPickedUpAt(LocalDateTime.now().withNano(0));
        }
        stateMachine.transition(id, action.getExpectedStatus(), action.getTargetStatus(), actor, action.getLabel());
    }
    private List<Long> assignedIds(String email) {
        var scope = stores.getAssignedStores(email);
        if (scope.isEmpty()) throw new AccessDeniedException("Tài khoản chưa được phân công chi nhánh hoạt động.");
        return scope.stream().map(StoreResponse::id).toList();
    }
    private static void requirePickup(Order order) {
        if (order.getFulfillmentType() != FulfillmentType.STORE_PICKUP)
            throw new BadRequestException("Thao tác này chỉ dành cho đơn nhận tại cửa hàng.");
        if (order.getPaymentMethod() != PaymentMethod.ONLINE && order.getPaymentMethod() != PaymentMethod.PAY_AT_STORE)
            throw new BadRequestException("Nhận tại cửa hàng chỉ hỗ trợ ONLINE hoặc PAY_AT_STORE.");
    }
    private static boolean paymentAllows(Order order) {
        return order.getPaymentMethod() == PaymentMethod.ONLINE && order.getPaymentStatus() == OrderPaymentStatus.PAID
                || order.getPaymentMethod() == PaymentMethod.PAY_AT_STORE && order.getPaymentStatus() == OrderPaymentStatus.UNPAID;
    }
    private static PickupAction nextAction(Order order) {
        return paymentAllows(order) ? Arrays.stream(PickupAction.values())
                .filter(a -> a.getExpectedStatus() == order.getOrderStatus()).findFirst().orElse(null) : null;
    }
}
