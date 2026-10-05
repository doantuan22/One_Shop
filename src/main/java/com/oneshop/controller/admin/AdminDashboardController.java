package com.oneshop.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Admin area ({@code /admin/**}, already restricted to ADMIN by SecurityConfig). Phase 4 only provides the sample page that
 * exercises the Admin layout.
 *
 * <p>Later phases add the real Dashboard, Store, Staff assignment, Product/SKU, StoreProduct, Order and Review management here
 * (Phases 6 and 11).
 */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    @GetMapping
    public String dashboard() {
        return "admin/dashboard";
    }
}
