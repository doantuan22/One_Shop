package com.oneshop.controller.client;

import com.oneshop.service.CustomerOrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

/** Customer order pages are read-only. Completion belongs exclusively to assigned Staff. */
@Controller
@RequestMapping("/orders")
public class CustomerOrderController {
    private final CustomerOrderService orders;
    public CustomerOrderController(CustomerOrderService orders) { this.orders = orders; }
    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("customerOrders", orders.getOrders(principal.getName())); return "orders/index";
    }
    @GetMapping("/{orderId}")
    public String detail(@PathVariable Long orderId, Principal principal, Model model) {
        model.addAttribute("orderDetail", orders.getOrder(principal.getName(), orderId)); return "orders/detail";
    }
}
