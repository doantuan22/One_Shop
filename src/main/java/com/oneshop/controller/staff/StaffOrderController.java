package com.oneshop.controller.staff;

import com.oneshop.service.StaffOperationsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** General, read-only Order queue; existing delivery/pickup action controllers remain separate. */
@Controller
@RequestMapping("/staff/orders")
public class StaffOrderController {
    private final StaffOperationsService operations;
    public StaffOrderController(StaffOperationsService operations) { this.operations = operations; }

    @GetMapping
    public String orders(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orders", operations.getOrders(page));
        return "staff/orders/index";
    }

    @GetMapping("/{orderId}")
    public String order(@PathVariable Long orderId, Model model) {
        var response = operations.getOrder(orderId);
        model.addAttribute("detail", response.detail());
        model.addAttribute("customerName", response.customerName());
        return "staff/orders/detail";
    }
}
