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
    public CustomerOrderController(CustomerOrderService orders, OrderTransitionPolicy policy, ReviewService reviews,
                                   OrderRefundViewService refundView, CustomerOrderReturnService returns) {
        this.orders = orders; this.policy = policy; this.reviews = reviews; this.refundView = refundView;
        this.returns = returns;
    }
    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("customerOrders", orders.getOrders(principal.getName())); return "orders/index";
    }
    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId, Principal principal, Model model) {
        var detail = orders.getOrder(principal.getName(), orderId);
        var order = detail.order();
        model.addAttribute("orderDetail", detail);
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
        orders.cancelOrder(principal.getName(), orderId); return "redirect:/orders/" + orderId + "?cancelled";
    }
}
