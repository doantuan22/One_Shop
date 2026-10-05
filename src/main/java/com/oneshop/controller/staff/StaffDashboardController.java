package com.oneshop.controller.staff;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Staff area ({@code /staff/**}, already restricted to STAFF/ADMIN by SecurityConfig). Phase 4 only provides the sample page that
 * exercises the Staff layout.
 *
 * <p>Later phases add the real Dashboard, Order queue, Pickup queue, Stock and Inventory history here (Phase 10). The Store of
 * every request must come from the Staff's ACTIVE StaffStoreAssignment resolved in the Service, never from a request
 * parameter (BR-14).
 */
@Controller
@RequestMapping("/staff")
public class StaffDashboardController {

    @GetMapping
    public String dashboard() {
        return "staff/dashboard";
    }
}
