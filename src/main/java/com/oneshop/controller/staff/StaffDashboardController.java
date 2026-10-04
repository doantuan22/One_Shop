package com.oneshop.controller.staff;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Staff area ({@code /staff/**}, already restricted to STAFF/ADMIN by SecurityConfig). Skeleton only.
 *
 * <p>Later phases add Dashboard, Order queue, Pickup queue, Stock and Inventory history here (Phase 10). The Store of
 * every request must come from the Staff's ACTIVE StaffStoreAssignment resolved in the Service, never from a request
 * parameter (BR-14).
 */
@Controller
@RequestMapping("/staff")
public class StaffDashboardController {
}
