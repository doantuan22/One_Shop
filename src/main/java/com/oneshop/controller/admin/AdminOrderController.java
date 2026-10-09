package com.oneshop.controller.admin;

import com.oneshop.service.AdminOperationsService;
import com.oneshop.service.StoreService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {
    private final AdminOperationsService operations;
    private final StoreService stores;
    public AdminOrderController(AdminOperationsService operations, StoreService stores) { this.operations = operations; this.stores = stores; }
    @GetMapping
    public String list(@RequestParam(required = false) Long storeId, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orders", operations.getOrders(storeId, page));
        model.addAttribute("stores", stores.getAllStores()); model.addAttribute("storeId", storeId); return "admin/orders";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("orderDetail", operations.getOrder(id)); return "admin/order-detail";
    }
}
