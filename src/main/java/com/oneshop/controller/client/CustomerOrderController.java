package com.oneshop.controller.client;

import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.OrderTransitionPolicy;
import com.oneshop.service.ReviewService;
import com.oneshop.service.payment.OrderRefundViewService;
import com.oneshop.service.returns.CustomerOrderReturnService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

/** Customer reads and cancellation before preparation. Completion belongs exclusively to assigned Staff. */
@Controller
@RequestMapping("/orders")
public class CustomerOrderController {
    private final CustomerOrderService orders;
    private final OrderTransitionPolicy policy;
    private final ReviewService reviews;
    private final OrderRefundViewService refundView;
    private final CustomerOrderReturnService returns;
    private final com.oneshop.service.PaymentService payments;
    private final com.oneshop.service.payment.PaymentReconciliationService reconciliation;
    private com.oneshop.service.payment.PaymentEvidenceRefresher refresher;

    /** Present only with a real gateway: the provider is asked (no lock held) before the order is cancelled for non-payment. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRefresher(com.oneshop.service.payment.PaymentEvidenceRefresher refresher) {
        this.refresher = refresher;
    }

    public CustomerOrderController(CustomerOrderService orders, OrderTransitionPolicy policy, ReviewService reviews,
                                   OrderRefundViewService refundView, CustomerOrderReturnService returns,
                                   com.oneshop.service.PaymentService payments,
                                   com.oneshop.service.payment.PaymentReconciliationService reconciliation) {
        this.orders = orders; this.policy = policy; this.reviews = reviews; this.refundView = refundView;
        this.returns = returns; this.payments = payments; this.reconciliation = reconciliation;
    }
    @GetMapping
    public String list(Principal principal, Model model) {
        var customerOrders = orders.getOrders(principal.getName());
        model.addAttribute("customerOrders", customerOrders);
        model.addAttribute("orderNumbers", orders.getOrderNumbers(principal.getName()));
        model.addAttribute("orderItems", orders.getOrderItems(principal.getName()));
        return "orders/index";
    }

    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId, Principal principal, Model model) {
        var detail = orders.getOrder(principal.getName(), orderId);
        var order = detail.order();
        if (order.paymentMethod() == com.oneshop.entity.PaymentMethod.ONLINE
                && order.orderStatus() == com.oneshop.entity.OrderStatus.PENDING_PAYMENT) {
            var paymentInfo = payments.getOrderPayments(principal.getName(), orderId);
            if (paymentInfo.expired() && (refresher == null || refresher.refresh(orderId))) {
                // the 15 minutes are over (and, with real money, the provider confirmed the state): cancel the unpaid
                // order and give the stock back now
                reconciliation.reconcile(orderId, com.oneshop.service.payment.PaymentReconciliationService.Trigger.TIMEOUT_SCAN);
                detail = orders.getOrder(principal.getName(), orderId);
                order = detail.order();
            } else if (!paymentInfo.expired()) {
                model.addAttribute("paymentInfo", paymentInfo);
            }
        }
        model.addAttribute("orderDetail", detail);
        model.addAttribute("orderNumber", orders.getOrderNumbers(principal.getName()).get(orderId));
        var info = orders.getOrderItems(principal.getName()).get(orderId);
        model.addAttribute("orderLines", info == null ? null : info.lines());
        model.addAttribute("refund", refundView.forCustomer(principal.getName(), orderId));
        model.addAttribute("reviewItems", reviews.getOrderReviewItems(principal.getName(), orderId));
        // post-delivery return: the request already made, or whether one may be made now (3 days from receipt)
        var orderReturn = returns.find(principal.getName(), orderId);
        model.addAttribute("orderReturn", orderReturn.orElse(null));
        model.addAttribute("returnEligibility", orderReturn.isPresent() ? null : returns.eligibility(principal.getName(), orderId));
        model.addAttribute("canCancel", policy.canCustomerCancel(order.orderStatus(), order.fulfillmentType(),
                order.paymentMethod(), order.paymentStatus()));
        return "orders/detail";
    }
    @PostMapping("/{orderId}/cancel")
    public String cancel(@PathVariable Long orderId, Principal principal) {
        if (refresher != null && !refresher.refresh(orderId)) {
            // the cancel button is not proof that no money moved: with real money, ask the provider first
            throw new com.oneshop.exception.PaymentConflictException(
                    com.oneshop.exception.PaymentConflictException.GATEWAY_UNAVAILABLE,
                    "Chưa xác minh được trạng thái thanh toán với payOS nên chưa thể hủy đơn. Vui lòng thử lại sau ít phút.");
        }
        var result = orders.cancelOrder(principal.getName(), orderId);
        // the order is cancelled either way; only a refund that still has to be retried is worth a second notice
        return "redirect:/orders/" + orderId + switch (result) {
            case CANCELLED_REFUND_FAILED -> "?cancelled&refund=pending";
            case CANCELLED_REFUND_REQUESTED -> "?cancelled&refund=requested";
            default -> "?cancelled";
        };
    }
}
