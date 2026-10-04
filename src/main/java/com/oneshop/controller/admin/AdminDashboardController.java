package com.oneshop.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Admin area ({@code /admin/**}, already restricted to ADMIN by SecurityConfig). Skeleton only.
 *
 * <p>Later phases add Store, Staff assignment, Product/SKU, StoreProduct, Order and Review management here
 * (Phases 6 and 11).
 */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {
}
