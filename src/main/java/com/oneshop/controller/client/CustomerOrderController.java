package com.oneshop.controller.client;

import com.oneshop.service.CustomerOrderService;
import com.oneshop.service.OrderTransitionPolicy;
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
    public CustomerOrderController(CustomerOrderService orders, OrderTransitionPolicy policy) {
        this.orders = orders; this.policy = policy;
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
        model.addAttribute("canCancel", policy.canCustomerCancel(order.orderStatus(), order.fulfillmentType(),
                order.paymentMethod(), order.paymentStatus()));
        return "orders/detail";
    }
    @PostMapping("/{orderId}/cancel")
    public String cancel(@PathVariable Long orderId, Principal principal) {
        orders.cancelOrder(principal.getName(), orderId); return "redirect:/orders/" + orderId + "?cancelled";
    }
}
