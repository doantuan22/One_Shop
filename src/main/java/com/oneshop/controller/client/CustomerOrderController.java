package com.oneshop.controller.client;

import com.oneshop.entity.OrderStatus;
import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.OrderTransitionPolicy;
import com.oneshop.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

/** Customer reads and cancellation before preparation. Completion belongs exclusively to assigned Staff. */
@Controller
@RequestMapping("/orders")
public class CustomerOrderController {
    private final CustomerOrderService orders;
    private final OrderTransitionPolicy policy;
    private final ReviewService reviews;
    public CustomerOrderController(CustomerOrderService orders, OrderTransitionPolicy policy, ReviewService reviews) {
        this.orders = orders; this.policy = policy; this.reviews = reviews;
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
        // only a COMPLETED Order can be reviewed; the Service re-checks ownership and status
        model.addAttribute("reviewItems", order.orderStatus() == OrderStatus.COMPLETED
                ? reviews.getOrderReviewItems(principal.getName(), orderId) : List.of());
        model.addAttribute("canCancel", policy.canCustomerCancel(order.orderStatus(), order.fulfillmentType(),
                order.paymentMethod(), order.paymentStatus()));
        return "orders/detail";
    }
    @PostMapping("/{orderId}/cancel")
    public String cancel(@PathVariable Long orderId, Principal principal) {
        orders.cancelOrder(principal.getName(), orderId); return "redirect:/orders/" + orderId + "?cancelled";
    }
}
