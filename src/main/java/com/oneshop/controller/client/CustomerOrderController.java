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
        model.addAttribute("orderNumbers", orderNumbers(customerOrders));
        model.addAttribute("orderItems", orders.getOrderItems(principal.getName()));
        return "orders/index";
    }

    /**
     * What the customer sees as the order number: their own running count (first order = 1, then 2, 3...), not the
     * database id. The list is newest first, so the oldest order gets 1. Display only; links still use the real id.
     */
    private static java.util.Map<Long, Integer> orderNumbers(java.util.List<com.oneshop.dto.response.OrderViewResponse> list) {
        java.util.Map<Long, Integer> numbers = new java.util.HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            numbers.put(list.get(i).orderId(), list.size() - i);
        }
        return numbers;
    }
    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId, Principal principal, Model model) {
        var detail = orders.getOrder(principal.getName(), orderId);
        var order = detail.order();
        if (order.paymentMethod() == com.oneshop.entity.PaymentMethod.ONLINE
                && order.orderStatus() == com.oneshop.entity.OrderStatus.PENDING_PAYMENT) {
            var paymentInfo = payments.getOrderPayments(principal.getName(), orderId);
            if (paymentInfo.expired()) {
                // the 15 minutes are over: cancel the unpaid order and give the stock back now
                reconciliation.reconcile(orderId, com.oneshop.service.payment.PaymentReconciliationService.Trigger.TIMEOUT_SCAN);
                detail = orders.getOrder(principal.getName(), orderId);
                order = detail.order();
            } else {
                model.addAttribute("paymentInfo", paymentInfo);
            }
        }
        model.addAttribute("orderDetail", detail);
        model.addAttribute("orderNumber", orderNumbers(orders.getOrders(principal.getName())).get(orderId));
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
        var result = orders.cancelOrder(principal.getName(), orderId);
        // the order is cancelled either way; only a refund that still has to be retried is worth a second notice
        return "redirect:/orders/" + orderId + (result == com.oneshop.service.payment.CustomerOrderCancellationService
                .Result.CANCELLED_REFUND_FAILED ? "?cancelled&refund=pending" : "?cancelled");
    }
}
