package com.oneshop.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import com.oneshop.service.AdminOperationsService;
import com.oneshop.service.StoreService;

/**
 * Admin chain overview: per-Store metrics from existing SQL data, including inactive Stores.
 */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final AdminOperationsService operations;
    private final StoreService stores;

    public AdminDashboardController(AdminOperationsService operations, StoreService stores) {
        this.operations = operations; this.stores = stores;
    }

    @GetMapping
    public String dashboard(@RequestParam(required = false) Long storeId, Model model) {
        model.addAttribute("overview", operations.getOverview(storeId));
        model.addAttribute("stores", stores.getAllStores());
        model.addAttribute("storeId", storeId);
        return "admin/dashboard";
    }
}
